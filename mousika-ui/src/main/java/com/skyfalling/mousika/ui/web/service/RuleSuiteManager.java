package com.skyfalling.mousika.ui.web.service;

import com.skyfalling.mousika.engine.RuleDefinition;
import com.skyfalling.mousika.engine.UdfDefinition;
import com.skyfalling.mousika.eval.result.NodeResult;
import com.skyfalling.mousika.suite.RuleFlowDefinition;
import com.skyfalling.mousika.suite.RuleSuite;
import com.skyfalling.mousika.udf.EvalFlowUdf;
import com.skyfalling.mousika.ui.tree.node.TreeNode;
import com.skyfalling.mousika.ui.web.common.BusinessException;
import com.skyfalling.mousika.ui.web.dao.RuleDefinitionDao;
import com.skyfalling.mousika.ui.web.dao.RuleFlowDao;
import com.skyfalling.mousika.ui.web.entity.RuleDefinitionEntity;
import com.skyfalling.mousika.ui.web.entity.RuleFlowEntity;
import com.skyfalling.mousika.utils.JsonUtils;
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
 * 启动时初次装配；规则或规则流写操作提交后异步触发 {@link #refresh()}。
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
    // 依赖迁移器仅为保证启动顺序：DbMigrator 的 @PostConstruct 补列先于本类装配 RuleSuite。
    private final DbMigrator dbMigrator;

    private volatile RuleSuite current;
    private final ReentrantLock refreshLock = new ReentrantLock();

    @PostConstruct
    public void init() {
        refresh();
    }

    public RuleSuite getSuite() {
        RuleSuite s = current;
        if (s == null) {
            refresh();
            s = current;
        }
        return s;
    }

    /**
     * 在当前事务提交后再触发 refresh，避免读到未落地的数据。
     * 若不在事务上下文中，退化为立即刷新。
     */
    public void refreshAsyncAfterCommit() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        refresh();
                    } catch (Exception e) {
                        log.error("refresh RuleSuite failed", e);
                    }
                }
            });
        } else {
            refresh();
        }
    }

    /**
     * 全量重建 RuleSuite：读所有启用中的规则与规则流，构造 {@link RuleDefinition}
     * 与 {@link RuleFlowDefinition} 传给 {@link RuleSuite} 构造函数。
     */
    public void refresh() {
        refreshLock.lock();
        try {
            List<RuleDefinition> ruleDefs = new ArrayList<>();
            for (RuleDefinitionEntity e : ruleDao.listActive()) {
                RuleDefinition rd = new RuleDefinition(
                        String.valueOf(e.getId()),
                        e.getExpression(),
                        e.getDescription() == null ? "" : e.getDescription());
                rd.setUseType(e.getUseType() == null ? 0 : e.getUseType());
                ruleDefs.add(rd);
            }

            List<RuleFlowDefinition> flowDefs = new ArrayList<>();
            for (RuleFlowEntity f : flowDao.listActive()) {
                try {
                    TreeNode tree = JsonUtils.toBean(f.getRuleTree(), TreeNode.class);
                    String dsl = tree.toRule().expr();
                    flowDefs.add(new RuleFlowDefinition(String.valueOf(f.getId()), dsl));
                } catch (Exception ex) {
                    log.warn("skip malformed flow id={} name={}: {}", f.getId(), f.getName(), ex.getMessage());
                }
            }

            List<UdfDefinition> udfDefs = defaultUdfs();

            RuleSuite next = new RuleSuite(ruleDefs, udfDefs, flowDefs);
            this.current = next;
            log.info("RuleSuite refreshed: {} rules, {} flows, {} udfs",
                    ruleDefs.size(), flowDefs.size(), udfDefs.size());
        } finally {
            refreshLock.unlock();
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
