package com.skyfalling.mosika.ui.web.service;

import com.skyfalling.mosika.engine.RuleDefinition;
import com.skyfalling.mosika.engine.UdfDefinition;
import com.skyfalling.mosika.eval.result.NodeResult;
import com.skyfalling.mosika.suite.RuleSuite;
import com.skyfalling.mosika.ui.tree.node.TreeNode;
import com.skyfalling.mosika.ui.web.common.BusinessException;
import com.skyfalling.mosika.ui.web.dao.AtomicRuleDao;
import com.skyfalling.mosika.ui.web.dao.FlowReferenceDao;
import com.skyfalling.mosika.ui.web.dao.RuleFlowDao;
import com.skyfalling.mosika.ui.web.dao.UdfDefinitionDao;
import com.skyfalling.mosika.ui.web.entity.AtomicRuleEntity;
import com.skyfalling.mosika.ui.web.entity.RuleFlowEntity;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * 从 Web 存储装配并维护全局 {@link RuleSuite} 运行快照
 * <p>
 * AtomicRule 和 RuleFlow 在存储层分表管理，对外 ID 分别由 {@code r + id} 和
 * {@code f + id} 派生后转换为 Core {@link RuleDefinition}，因此运行态只需要一个 RuleSuite
 * <p>
 * 命名空间只限制 Web 层的新引用关系，不参与 Core 编译、求值或套件分片
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RuleSuiteManager {

    /** 原子规则持久化访问对象 */
    private final AtomicRuleDao atomicRuleDao;

    /** 规则流持久化访问对象 */
    private final RuleFlowDao flowDao;

    /** 规则流引用索引 */
    private final FlowReferenceDao referenceDao;

    /** 用户 JavaScript UDF 持久化访问对象 */
    private final UdfDefinitionDao udfDao;

    /** 串行化完整候选套件的查询和编译过程 */
    private final ReentrantLock refreshLock = new ReentrantLock();

    /** 当前已经完成编译并对执行请求可见的全局规则套件 */
    private volatile RuleSuite currentSuite;

    /** Spring 完成依赖注入后装配第一份运行快照 */
    @PostConstruct
    public void init() {
        refresh();
    }

    /** 获取当前全局运行快照 */
    public RuleSuite getSuite() {
        RuleSuite suite = currentSuite;
        if (suite == null) {
            refresh();
            suite = currentSuite;
        }
        if (suite == null) {
            throw new IllegalStateException("RuleSuite is not initialized");
        }
        return suite;
    }

    /** 在当前事务内构造候选快照并在提交后发布 */
    public void refreshAfterCommit() {
        PreparedSuite prepared;
        try {
            prepared = prepareSuite();
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
            activate(prepareSuite());
        } catch (Exception e) {
            log.error("refresh RuleSuite failed, keep previous snapshot", e);
            throw new IllegalStateException("RuleSuite refresh failed: " + rootMessage(e), e);
        }
    }

    /** 查询运行态定义并完成候选套件全量编译 */
    private PreparedSuite prepareSuite() {
        refreshLock.lock();
        try {
            List<AtomicRuleEntity> atomics = runtimeAtomics();
            List<RuleFlowEntity> flows = runtimeFlows();
            List<RuleDefinition> definitions = new ArrayList<>(atomics.size() + flows.size());
            definitions.addAll(toAtomicDefinitions(atomics));
            definitions.addAll(toFlowDefinitions(flows));
            List<UdfDefinition> udfs = runtimeUdfs();
            return new PreparedSuite(new RuleSuite(definitions, udfs), atomics.size(), flows.size(), udfs.size());
        } finally {
            refreshLock.unlock();
        }
    }

    /** 加载启用原子规则以及运行态规则流闭包引用的停用原子规则 */
    private List<AtomicRuleEntity> runtimeAtomics() {
        List<AtomicRuleEntity> atomics = new ArrayList<>(atomicRuleDao.listActive());
        Set<Long> loaded = atomics.stream().map(AtomicRuleEntity::getId).collect(Collectors.toSet());
        Set<Long> missing = new HashSet<>(referenceDao.runtimeRuleIds());
        missing.removeAll(loaded);
        atomics.addAll(atomicRuleDao.findByIds(missing));
        return atomics;
    }

    /** 加载生效规则流及其递归引用的停用规则流 */
    private List<RuleFlowEntity> runtimeFlows() {
        return flowDao.findByIds(referenceDao.runtimeFlowIds());
    }

    /** 原子发布已经完成编译的候选快照 */
    private void activate(PreparedSuite prepared) {
        currentSuite = prepared.suite();
        log.info("RuleSuite refreshed: {} atomic rules, {} flows, {} udfs",
                prepared.atomicCount(), prepared.flowCount(), prepared.udfCount());
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
            List<AtomicRuleEntity> atomics = runtimeAtomics();
            atomics.removeIf(active -> active.getId().equals(rule.getId()));
            atomics.add(rule);
            List<RuleDefinition> definitions = new ArrayList<>();
            definitions.addAll(toAtomicDefinitions(atomics));
            definitions.addAll(toFlowDefinitions(runtimeFlows()));
            new RuleSuite(definitions, runtimeUdfs());
        } catch (Exception e) {
            throw new BusinessException(400, "rule expression compile failed: " + rootMessage(e));
        }
    }

    /** 把已启用数据库 UDF 转换为 Core UDF 定义 */
    private List<UdfDefinition> runtimeUdfs() {
        List<UdfDefinition> definitions = new ArrayList<>();
        for (UdfDefinitionEntity entity : udfDao.listActive()) {
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
            return getSuite().evalRule(flowId, target);
        }
        return getSuite().evalRule(flowId, target, context);
    }

    /** 执行一条已启用原子规则 */
    public NodeResult evalRule(String ruleId, Object target) {
        AtomicRuleEntity rule = atomicRuleDao.findByRuleId(ruleId);
        if (rule == null || rule.getStatus() == null || rule.getStatus() != 1) {
            throw new BusinessException(404, "active rule not found: " + ruleId);
        }
        return getSuite().evalRule(ruleId, target);
    }

    /** 在全局套件中直接评估一段规则 DSL */
    public NodeResult evalExpr(String expression, Object target) {
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException("expression is required");
        }
        return getSuite().eval(expression, target);
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
    private record PreparedSuite(RuleSuite suite, int atomicCount, int flowCount, int udfCount) {
    }
}
