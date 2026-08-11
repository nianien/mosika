package com.skyfalling.mosika.ui.web.service;

import com.skyfalling.mosika.engine.RuleDefinition;
import com.skyfalling.mosika.engine.UdfDefinition;
import com.skyfalling.mosika.eval.result.NodeResult;
import com.skyfalling.mosika.suite.RuleSuite;
import com.skyfalling.mosika.ui.tree.node.TreeNode;
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
            definitions.add(new UdfDefinition(entity.getGroup(), entity.getName(), entity.getSource()));
        }
        return definitions;
    }

    /** 执行一条已经生效的规则流 */
    public NodeResult evalFlow(String flowId, Object target, Map<String, Object> context) {
        RuleFlowEntity flow = flowDao.findByFlowId(flowId);
        if (flow == null || flow.getStatus() == null || flow.getStatus() != RuleFlowService.PUBLISHED) {
            throw new BusinessException(404, "active flow not found: " + flowId);
        }
        if (context == null || context.isEmpty()) {
            return getSuite(flow.getNamespace()).evalRule(flowId, target);
        }
        return getSuite(flow.getNamespace()).evalRule(flowId, target, context);
    }

    /** 执行一条已启用原子规则 */
    public NodeResult evalRule(String ruleId, Object target) {
        AtomicRuleEntity rule = atomicRuleDao.findByRuleId(ruleId);
        if (rule == null || rule.getStatus() == null || rule.getStatus() != 1) {
            throw new BusinessException(404, "active rule not found: " + ruleId);
        }
        return getSuite(rule.getNamespace()).evalRule(ruleId, target);
    }

    /** 在指定命名空间套件中直接评估一段规则 DSL */
    public NodeResult evalExpr(String namespace, String expression, Object target) {
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException("expression is required");
        }
        return getSuite(namespace).eval(expression, target);
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
