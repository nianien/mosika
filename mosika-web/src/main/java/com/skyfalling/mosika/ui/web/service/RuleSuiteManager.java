package com.skyfalling.mosika.ui.web.service;

import com.skyfalling.mosika.engine.RuleDefinition;
import com.skyfalling.mosika.engine.UdfDefinition;
import com.skyfalling.mosika.eval.node.ExprNode;
import com.skyfalling.mosika.eval.node.RuleNode;
import com.skyfalling.mosika.eval.result.NodeResult;
import com.skyfalling.mosika.eval.result.RuleResult;
import com.skyfalling.mosika.suite.RuleSuite;
import com.skyfalling.mosika.ui.tree.node.TreeNode;
import com.skyfalling.mosika.ui.tree.node.define.UINode;
import com.skyfalling.mosika.ui.tree.node.flow.ANode;
import com.skyfalling.mosika.ui.tree.node.flow.CNode;
import com.skyfalling.mosika.ui.tree.node.flow.DNode;
import com.skyfalling.mosika.ui.tree.node.flow.PNode;
import com.skyfalling.mosika.ui.tree.node.flow.SNode;
import com.skyfalling.mosika.ui.web.common.BusinessException;
import com.skyfalling.mosika.ui.web.config.DatabaseMigrationInitializer;
import com.skyfalling.mosika.ui.web.dao.AtomicRuleDao;
import com.skyfalling.mosika.ui.web.dao.FlowReferenceDao;
import com.skyfalling.mosika.ui.web.dao.RuleFlowDao;
import com.skyfalling.mosika.ui.web.dao.RuleNamespaceDao;
import com.skyfalling.mosika.ui.web.dao.UdfDefinitionDao;
import com.skyfalling.mosika.ui.web.entity.AtomicRuleEntity;
import com.skyfalling.mosika.ui.web.entity.RuleFlowEntity;
import com.skyfalling.mosika.ui.web.entity.RuleNamespaceEntity;
import com.skyfalling.mosika.ui.web.entity.UdfDefinitionEntity;
import com.skyfalling.mosika.utils.Constants;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * 从 Web 存储装配并维护按命名空间隔离的 {@link RuleSuite} 运行快照
 * <p>
 * 每个启用命名空间分别装配一份 RuleSuite，命名空间同时约束规则、规则流和 UDF，
 * 是 Core 编译与求值的运行态隔离边界
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RuleSuiteManager {

    /** 试运行临时预览规则的 ID，不与真实规则 ID 命名空间冲突 */
    private static final String TRY_RULE_ID = "tryRulePreview";

    /** 数据库结构迁移入口，确保套件初始化前已完成迁移和 schema 初始化 */
    private final DatabaseMigrationInitializer databaseMigrationInitializer;

    /** 原子规则持久化访问对象 */
    private final AtomicRuleDao atomicRuleDao;

    /** 规则流持久化访问对象 */
    private final RuleFlowDao flowDao;

    /** 规则流引用索引 */
    private final FlowReferenceDao referenceDao;

    /** 用户 JavaScript UDF 持久化访问对象 */
    private final UdfDefinitionDao udfDao;

    /** 命名空间持久化访问对象 */
    private final RuleNamespaceDao namespaceDao;

    /** 串行化完整候选套件的查询和编译过程 */
    private final ReentrantLock refreshLock = new ReentrantLock();

    /** 当前已经完成编译并对执行请求可见的命名空间规则套件 */
    private volatile Map<String, RuleSuite> currentSuites;

    /** Spring 完成依赖注入后装配第一份运行快照 */
    @PostConstruct
    public void init() {
        refresh();
    }

    /** 获取指定命名空间的当前运行快照 */
    public RuleSuite getSuite(String namespace) {
        Map<String, RuleSuite> suites = currentSuites;
        if (suites == null) {
            refresh();
            suites = currentSuites;
        }
        if (suites == null) {
            throw new IllegalStateException("RuleSuite is not initialized");
        }
        RuleSuite suite = suites.get(namespace);
        if (suite == null) {
            throw new BusinessException(404, "namespace not found or disabled: " + namespace);
        }
        return suite;
    }

    /** 在当前事务内构造候选快照并在提交后发布 */
    public void refreshAfterCommit() {
        PreparedSuites prepared;
        try {
            prepared = prepareSuites();
        } catch (Exception e) {
            throw new BusinessException(400, "RuleSuite refresh validation failed: " + rootMessage(e));
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    activate(prepared);
                }
            });
        } else {
            activate(prepared);
        }
    }

    /** 从已提交数据库状态全量重建并发布全局 RuleSuite */
    public void refresh() {
        try {
            activate(prepareSuites());
        } catch (Exception e) {
            log.error("refresh RuleSuite failed, keep previous snapshot", e);
            throw new IllegalStateException("RuleSuite refresh failed: " + rootMessage(e), e);
        }
    }

    /** 查询运行态定义并完成候选套件全量编译 */
    private PreparedSuites prepareSuites() {
        refreshLock.lock();
        try {
            Map<String, RuleSuite> suites = new LinkedHashMap<>();
            int atomicCount = 0;
            int flowCount = 0;
            int udfCount = 0;
            for (RuleNamespaceEntity namespace : namespaceDao.list()) {
                if (namespace.getStatus() == null || namespace.getStatus() != 1) {
                    continue;
                }
                try {
                    List<AtomicRuleEntity> atomics = runtimeAtomics(namespace.getCode(), namespace.getId());
                    List<RuleFlowEntity> flows = runtimeFlows(namespace.getId());
                    List<RuleDefinition> definitions = new ArrayList<>(atomics.size() + flows.size());
                    definitions.addAll(toAtomicDefinitions(atomics));
                    definitions.addAll(toFlowDefinitions(flows));
                    List<UdfDefinition> udfs = runtimeUdfs(namespace.getCode());
                    suites.put(namespace.getCode(), new RuleSuite(definitions, udfs));
                    atomicCount += atomics.size();
                    flowCount += flows.size();
                    udfCount += udfs.size();
                } catch (Exception e) {
                    IllegalStateException scoped = new IllegalStateException(
                            "namespace " + namespace.getCode() + ": " + rootMessage(e));
                    scoped.addSuppressed(e);
                    throw scoped;
                }
            }
            return new PreparedSuites(suites, atomicCount, flowCount, udfCount);
        } finally {
            refreshLock.unlock();
        }
    }

    /** 加载启用原子规则以及运行态规则流闭包引用的停用原子规则 */
    private List<AtomicRuleEntity> runtimeAtomics(String namespace, long namespaceId) {
        List<AtomicRuleEntity> atomics = new ArrayList<>(atomicRuleDao.listActive(namespace));
        Set<Long> loaded = atomics.stream().map(AtomicRuleEntity::getId).collect(Collectors.toSet());
        Set<Long> missing = new HashSet<>(referenceDao.runtimeRuleIds(namespaceId));
        missing.removeAll(loaded);
        atomics.addAll(atomicRuleDao.findByIds(missing));
        return atomics;
    }

    /** 加载生效规则流及其递归引用的停用规则流 */
    private List<RuleFlowEntity> runtimeFlows(long namespaceId) {
        return flowDao.findByIds(referenceDao.runtimeFlowIds(namespaceId));
    }

    /** 原子发布已经完成编译的候选快照 */
    private void activate(PreparedSuites prepared) {
        currentSuites = prepared.suites();
        log.info("RuleSuites refreshed: {} namespaces, {} atomic rules, {} flows, {} udfs",
                prepared.suites().size(), prepared.atomicCount(), prepared.flowCount(), prepared.udfCount());
    }

    /** 把存储层原子规则转换为 Core 原子规则定义 */
    private List<RuleDefinition> toAtomicDefinitions(Collection<AtomicRuleEntity> entities) {
        List<RuleDefinition> definitions = new ArrayList<>(entities.size());
        for (AtomicRuleEntity entity : entities) {
            definitions.add(new RuleDefinition(entity.getRuleId(), entity.getExpression(),
                    entity.getDescription() == null ? "" : entity.getDescription(),
                    RuleDefinition.RULE_TYPE_ATOMIC));
        }
        return definitions;
    }

    /** 把存储层规则流编译为 Core 复合规则定义 */
    private List<RuleDefinition> toFlowDefinitions(Collection<RuleFlowEntity> flows) {
        List<RuleDefinition> definitions = new ArrayList<>(flows.size());
        for (RuleFlowEntity flow : flows) {
            TreeNode tree = TreeNode.fromJson(flow.getRuleTree());
            tree.validateSize(RuleTreeCompiler.MAX_TREE_DEPTH, RuleTreeCompiler.MAX_TOTAL_NODES);
            tree.validate();
            String description = flow.getDescription() == null ? flow.getName() : flow.getDescription();
            definitions.add(new RuleDefinition(flow.getFlowId(), tree.toRule().expr(), description,
                    RuleDefinition.RULE_TYPE_COMPOSITE));
        }
        return definitions;
    }

    /** 隔离校验未进入运行态的停用原子规则变更 */
    public void assertAtomicRuleBuildable(AtomicRuleEntity rule) {
        try {
            List<AtomicRuleEntity> atomics = runtimeAtomics(rule.getNamespace(), rule.getNamespaceId());
            atomics.removeIf(active -> active.getId().equals(rule.getId()));
            atomics.add(rule);
            List<RuleDefinition> definitions = new ArrayList<>();
            definitions.addAll(toAtomicDefinitions(atomics));
            definitions.addAll(toFlowDefinitions(runtimeFlows(rule.getNamespaceId())));
            new RuleSuite(definitions, runtimeUdfs(rule.getNamespace()));
        } catch (Exception e) {
            throw new BusinessException(400, "rule expression compile failed: " + rootMessage(e));
        }
    }

    /** 把已启用数据库 UDF 转换为 Core UDF 定义 */
    private List<UdfDefinition> runtimeUdfs(String namespace) {
        List<UdfDefinition> definitions = new ArrayList<>();
        for (UdfDefinitionEntity entity : udfDao.listActive(namespace)) {
            if ("__proto__".equals(entity.getName())) {
                throw new IllegalArgumentException("udf name is reserved: " + entity.getName());
            }
            String group = entity.getGroup();
            if ("__proto__".equals(group)
                    || group.startsWith("__proto__.")
                    || group.endsWith(".__proto__")
                    || group.contains(".__proto__.")) {
                throw new IllegalArgumentException("udf group is reserved: __proto__");
            }
            definitions.add(new UdfDefinition(entity.getGroup(), entity.getName(), entity.getSource()));
        }
        return definitions;
    }

    /** 执行一条已经生效的规则流 */
    public NodeResult evalFlow(String flowId, Object target, Map<String, Object> context) {
        RuleFlowEntity flow = flowDao.findActiveByFlowId(flowId);
        if (flow == null || flow.getStatus() == null || flow.getStatus() != RuleFlowService.PUBLISHED) {
            throw new BusinessException(404, "active flow not found: " + flowId);
        }
        return getSuite(flow.getNamespace()).eval(flowId, target, context);
    }

    /** 执行一条已启用原子规则 */
    public NodeResult evalRule(String ruleId, Object target) {
        AtomicRuleEntity rule = atomicRuleDao.findByRuleId(ruleId);
        if (rule == null || rule.getStatus() == null || rule.getStatus() != 1) {
            throw new BusinessException(404, "active rule not found: " + ruleId);
        }
        return getSuite(rule.getNamespace()).eval(ruleId, target);
    }

    /** 在指定命名空间套件中直接评估一段规则 DSL */
    public NodeResult evalExpr(String namespace, String expression, Object target) {
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException("expression is required");
        }
        return getSuite(namespace).eval(expression, target);
    }

    /**
     * 试运行一段原子规则表达式，规则可以尚未保存或尚未启用
     * <p>
     * Core 没有 (ruleId, target, args) 求值入口，$args 只能由 {@link ExprNode} 携带。
     * 这里用当前命名空间的已启用 UDF 加一条临时预览定义编译出一次性套件，
     * 与 {@link #assertAtomicRuleBuildable} 的编译方式一致，不触碰运行态快照。
     * <p>
     * 注意：core 的 {@code eval(RuleNode, data, context)} 不返回规则详情，
     * 因此传入上下文时结果里没有命中详情，不传上下文时才有。
     *
     * @param namespace  命名空间编码
     * @param expression 原子规则表达式（JavaScript）
     * @param argsJson   模板参数 JSON 对象文本，空表示不带参数
     * @param target     求值参数对象（$）
     * @param context    附加上下文（$$），可为空
     * @return 执行结果
     */
    public NodeResult tryRule(String namespace, String expression, String argsJson,
                              Object target, Map<String, Object> context) {
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException("expression is required");
        }
        RuleSuite suite;
        try {
            RuleDefinition preview = new RuleDefinition(TRY_RULE_ID, expression, "", RuleDefinition.RULE_TYPE_ATOMIC);
            suite = new RuleSuite(List.of(preview), runtimeUdfs(namespace));
        } catch (Exception e) {
            throw new BusinessException(400, "rule expression compile failed: " + rootMessage(e));
        }
        RuleNode node;
        try {
            node = argsJson == null || argsJson.isBlank()
                    ? new ExprNode(TRY_RULE_ID)
                    : new ExprNode(TRY_RULE_ID, argsJson);
        } catch (Exception e) {
            throw new BusinessException(400, "invalid rule arguments: " + rootMessage(e));
        }
        try {
            if (context == null || context.isEmpty()) {
                return suite.eval(node, target);
            }
            return suite.eval(node, target, context);
        } catch (Exception e) {
            throw new BusinessException(400, "rule eval failed: " + rootMessage(e));
        }
    }

    public FlowTryResult tryFlow(String namespace, String ruleTree,
                                 Object target, Map<String, Object> context) {
        RuleNamespaceEntity namespaceEntity = namespaceDao.findByCode(namespace);
        if (namespaceEntity == null || namespaceEntity.getStatus() == null
                || namespaceEntity.getStatus() != 1) {
            throw new BusinessException(404, "namespace not found or disabled: " + namespace);
        }

        TreeNode tree;
        List<RuleDefinition> definitions;
        FlowTraceBuilder traceBuilder;
        try {
            RuleTreeCompiler.CompileResult compiled = RuleTreeCompiler.compile(ruleTree);
            tree = TreeNode.fromJson(compiled.getCanonicalJson());
            definitions = new ArrayList<>();
            definitions.addAll(toAtomicDefinitions(
                    runtimeAtomics(namespace, namespaceEntity.getId())));
            definitions.addAll(toFlowDefinitions(runtimeFlows(namespaceEntity.getId())));
            traceBuilder = new FlowTraceBuilder(tracePrefix(definitions));
            String entryId = traceBuilder.defineTree(tree);
            definitions.addAll(traceBuilder.definitions());

            RuleSuite suite = new RuleSuite(definitions, runtimeUdfs(namespace));
            Map<String, Object> mutableContext = context == null
                    ? new LinkedHashMap<>()
                    : new LinkedHashMap<>(context);
            NodeResult result = suite.eval(entryId, target, mutableContext);
            List<String> paths = executedPaths(result.getDetails(), traceBuilder.paths());
            return new FlowTryResult(result, mutableContext, paths);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(400, "flow eval failed: " + rootMessage(e));
        }
    }

    private static String tracePrefix(List<RuleDefinition> definitions) {
        int suffix = 0;
        while (true) {
            String prefix = "tryFlowTrace" + suffix + "N";
            boolean conflict = definitions.stream()
                    .anyMatch(definition -> definition.getRuleId().startsWith(prefix));
            if (!conflict) {
                return prefix;
            }
            suffix++;
        }
    }

    private static List<String> executedPaths(List<RuleResult> details,
                                              Map<String, String> pathByRuleId) {
        Set<String> paths = new LinkedHashSet<>();
        collectExecutedPaths(details, pathByRuleId, paths);
        return new ArrayList<>(paths);
    }

    private static void collectExecutedPaths(List<RuleResult> details,
                                             Map<String, String> pathByRuleId,
                                             Set<String> paths) {
        for (RuleResult detail : details) {
            String expr = detail.getExpr();
            if (expr != null) {
                int boundary = expr.indexOf('[');
                String ruleId = boundary < 0 ? expr : expr.substring(0, boundary);
                String path = pathByRuleId.get(ruleId);
                if (path != null) {
                    paths.add(path);
                }
            }
            collectExecutedPaths(detail.getSubRules(), pathByRuleId, paths);
        }
    }

    private static final class FlowTraceBuilder {

        private final String prefix;
        private final List<RuleDefinition> definitions = new ArrayList<>();
        private final Map<String, String> paths = new LinkedHashMap<>();
        private int sequence;

        private FlowTraceBuilder(String prefix) {
            this.prefix = prefix;
        }

        private String defineTree(TreeNode tree) {
            String next = tree.getNext() == null ? Constants.NOP : defineNode(tree.getNext(), "$.next");
            return add("$", next);
        }

        private String defineNode(UINode node, String path) {
            if (node instanceof ANode action) {
                String expression = action.getRule().ruleExpr();
                if (action.getNext() != null) {
                    expression += "->" + defineNode(action.getNext(), path + ".next");
                }
                return add(path, expression);
            }
            if (node instanceof CNode condition) {
                String expression = condition.getRule().ruleExpr();
                if (condition.getNext() != null) {
                    expression += "?" + defineNode(condition.getNext(), path + ".next");
                }
                return add(path, expression);
            }
            if (node instanceof SNode serial) {
                List<String> expressions = new ArrayList<>();
                expressions.add(Constants.NOP);
                for (int i = 0; i < serial.getBranches().size(); i++) {
                    expressions.add(defineNode(serial.getBranches().get(i),
                            path + ".branches[" + i + "]"));
                }
                return add(path, String.join("->", expressions));
            }
            if (node instanceof PNode parallel) {
                List<String> expressions = new ArrayList<>();
                expressions.add(Constants.NOP);
                for (int i = 0; i < parallel.getBranches().size(); i++) {
                    expressions.add(defineNode(parallel.getBranches().get(i),
                            path + ".branches[" + i + "]"));
                }
                return add(path, String.join("=>", expressions));
            }
            if (node instanceof DNode decision) {
                String fallback = decision.getDefaultBranch() == null
                        ? null
                        : defineNode(decision.getDefaultBranch(), path + ".defaultBranch");
                for (int i = decision.getBranches().size() - 1; i >= 0; i--) {
                    CNode branch = decision.getBranches().get(i);
                    String branchPath = path + ".branches[" + i + "]";
                    String condition = add(branchPath, branch.getRule().ruleExpr());
                    String matched = branch.getNext() == null
                            ? Constants.NOP
                            : defineNode(branch.getNext(), branchPath + ".next");
                    fallback = condition + "?" + matched
                            + (fallback == null ? "" : ":(" + fallback + ")");
                }
                return add(path, fallback);
            }
            throw new UnsupportedOperationException(
                    "not support node type: " + node.getClass().getSimpleName());
        }

        private String add(String path, String expression) {
            String ruleId = prefix + sequence++;
            paths.put(ruleId, path);
            definitions.add(new RuleDefinition(
                    ruleId, expression, path, RuleDefinition.RULE_TYPE_COMPOSITE));
            return ruleId;
        }

        private List<RuleDefinition> definitions() {
            return definitions;
        }

        private Map<String, String> paths() {
            return paths;
        }
    }

    public record FlowTryResult(NodeResult result,
                                Map<String, Object> context,
                                List<String> executedPaths) {
    }

    /** 提取异常链最深层的稳定错误信息 */
    private static String rootMessage(Throwable error) {
        Throwable root = error;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
    }

    /** 已完成编译但尚未发布的规则套件快照 */
    private record PreparedSuites(Map<String, RuleSuite> suites,
                                  int atomicCount, int flowCount, int udfCount) {
    }
}
