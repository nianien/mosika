package com.skyfalling.mosika.ui.web.service;

import com.skyfalling.mosika.engine.RuleDefinition;
import com.skyfalling.mosika.engine.UdfDefinition;
import com.skyfalling.mosika.eval.result.NodeResult;
import com.skyfalling.mosika.suite.RuleFlowDefinition;
import com.skyfalling.mosika.suite.RuleSuite;
import com.skyfalling.mosika.udf.EvalFlowUdf;
import com.skyfalling.mosika.ui.tree.node.TreeNode;
import com.skyfalling.mosika.ui.web.common.BusinessException;
import com.skyfalling.mosika.ui.web.dao.RuleDefinitionDao;
import com.skyfalling.mosika.ui.web.dao.RuleFlowDao;
import com.skyfalling.mosika.ui.web.dao.FlowRuleRefDao;
import com.skyfalling.mosika.ui.web.entity.RuleDefinitionEntity;
import com.skyfalling.mosika.ui.web.entity.RuleFlowEntity;
import com.skyfalling.mosika.utils.JsonUtils;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 从库中装配并维护当前 {@link RuleSuite} 单例。
 * <p>
 * 启动时初次装配；规则或规则流写操作提交后触发 {@link #refresh()}。
 * 求值走 {@link #evalFlow(long, Object, Map)} / {@link #evalExpr(String, Object)}。
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RuleSuiteManager {

    private final RuleDefinitionDao ruleDao;
    private final RuleFlowDao flowDao;
    private final FlowRuleRefDao refDao;
    // 依赖迁移器仅为保证启动顺序：DbMigrator 的 @PostConstruct 补列先于本类装配 RuleSuite。
    private final DbMigrator dbMigrator;

    private final ReentrantLock refreshLock = new ReentrantLock();

    @PostConstruct
    public void init() {
        refresh();
    }

    public RuleSuite getSuite() {
        RuleSuite s = RuleSuite.get();
        if (s == null) {
            refresh();
            s = RuleSuite.get();
        }
        if (s == null) {
            throw new IllegalStateException("RuleSuite is not initialized");
        }
        return s;
    }

    /**
     * 在当前事务内先构造候选快照，构造失败会回滚数据库写入；事务提交后只做原子引用替换，
     * 避免“数据库已提交但运行态刷新失败”。若不在事务上下文中，直接构造并发布。
     */
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
                    publish(prepared);
                }
            });
        } else {
            publish(prepared);
        }
    }

    /**
     * 全量重建 RuleSuite：读所有启用中的规则与规则流，构造 {@link RuleDefinition}
     * 与 {@link RuleFlowDefinition} 传给 {@link RuleSuite} 构造函数。
     * <p>
     * 整体构建失败时保留上一份可用快照并向调用方抛错；显式刷新接口不得报告假成功。
     */
    public void refresh() {
        try {
            publish(prepareSuite());
        } catch (Exception e) {
            log.error("refresh RuleSuite failed, keep previous snapshot", e);
            throw new IllegalStateException("RuleSuite refresh failed: " + rootMessage(e), e);
        }
    }

    private PreparedSuite prepareSuite() {
        refreshLock.lock();
        try {
            // 运行态规则集合 = 启用中的规则 ∪ 被已生效场景引用的规则（即使规则已下架/停用）。
            // 这样"停用=下架、禁止被新场景引用"，但已经引用它的老场景仍能正常运行；
            // 新场景选不到它（下拉只列 listActive）。
            List<RuleDefinitionEntity> ruleEntities = new ArrayList<>(ruleDao.listActive());
            java.util.Set<Long> loadedIds = ruleEntities.stream()
                    .map(RuleDefinitionEntity::getId)
                    .collect(java.util.stream.Collectors.toSet());
            java.util.Set<Long> referenced = refDao.refCountsByActiveFlow().keySet();
            java.util.Set<Long> missing = new java.util.HashSet<>(referenced);
            missing.removeAll(loadedIds);
            if (!missing.isEmpty()) {
                ruleEntities.addAll(ruleDao.findByIds(missing));
            }
            List<RuleDefinition> ruleDefs = toRuleDefs(ruleEntities);
            List<RuleFlowDefinition> flowDefs = new ArrayList<>();
            for (RuleFlowEntity f : flowDao.listActive()) {
                TreeNode tree = JsonUtils.toBean(f.getRuleTree(), TreeNode.class);
                tree.validateSize(RuleTreeCompiler.MAX_TREE_DEPTH, RuleTreeCompiler.MAX_TOTAL_NODES);
                tree.validate();
                flowDefs.add(new RuleFlowDefinition(String.valueOf(f.getId()), tree.toRule().expr()));
            }
            List<UdfDefinition> udfDefs = defaultUdfs();
            RuleSuite candidate = RuleSuite.prepare(ruleDefs, udfDefs, flowDefs);
            return new PreparedSuite(candidate, ruleDefs.size(), flowDefs.size(), udfDefs.size());
        } finally {
            refreshLock.unlock();
        }
    }

    private void publish(PreparedSuite prepared) {
        RuleSuite.publish(prepared.suite());
        log.info("RuleSuite refreshed: {} rules, {} flows, {} udfs",
                prepared.ruleCount(), prepared.flowCount(), prepared.udfCount());
    }

    private static String rootMessage(Throwable error) {
        Throwable root = error;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
    }

    private record PreparedSuite(RuleSuite suite, int ruleCount, int flowCount, int udfCount) {
    }

    private List<RuleDefinition> toRuleDefs(java.util.Collection<RuleDefinitionEntity> entities) {
        List<RuleDefinition> ruleDefs = new ArrayList<>();
        for (RuleDefinitionEntity e : entities) {
            RuleDefinition rd = new RuleDefinition(
                    String.valueOf(e.getId()),
                    e.getExpression(),
                    e.getDescription() == null ? "" : e.getDescription());
            rd.setUseType(e.getUseType() == null ? 0 : e.getUseType());
            ruleDefs.add(rd);
        }
        return ruleDefs;
    }

    /**
     * 预编译校验：用给定的（预期启用）规则集合尝试构造一份候选 RuleSuite，
     * 构造失败即抛 {@link BusinessException}(400)，供规则写库前确认“新 RuleSuite 可构造”。
     */
    public void assertRulesBuildable(java.util.Collection<RuleDefinitionEntity> activeRules) {
        try {
            RuleSuite.validate(toRuleDefs(activeRules), defaultUdfs(), new ArrayList<>());
        } catch (Exception e) {
            throw new BusinessException(400, "rule expression compile failed: " + rootMessage(e));
        }
    }

    /** 默认 UDF：至少注册 EvalFlowUdf 支持 sys.flow.eval。 */
    private List<UdfDefinition> defaultUdfs() {
        List<UdfDefinition> list = new ArrayList<>();
        list.add(new UdfDefinition("sys.flow", "eval", new EvalFlowUdf()));
        return list;
    }

    public NodeResult evalFlow(long flowId, Object target, Map<String, Object> context) {
        RuleSuite suite = getSuite();
        if (context == null || context.isEmpty()) {
            return suite.evalFlow(String.valueOf(flowId), target);
        }
        return suite.evalFlow(String.valueOf(flowId), target, context);
    }

    public NodeResult evalRule(long ruleId, Object target) {
        RuleDefinitionEntity e = ruleDao.findById(ruleId);
        if (e == null || e.getStatus() == null || e.getStatus() != 1) {
            throw new BusinessException(404, "active rule not found: " + ruleId);
        }
        return getSuite().evalExpr(String.valueOf(ruleId), target);
    }

    public NodeResult evalExpr(String expression, Object target) {
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException("expression is required");
        }
        return getSuite().evalExpr(expression, target);
    }
}
