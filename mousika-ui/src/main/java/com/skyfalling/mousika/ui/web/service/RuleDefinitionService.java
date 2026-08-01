package com.skyfalling.mousika.ui.web.service;

import com.skyfalling.mousika.ui.web.common.BusinessException;
import com.skyfalling.mousika.ui.web.dao.FlowRuleRefDao;
import com.skyfalling.mousika.ui.web.dao.RuleDefinitionDao;
import com.skyfalling.mousika.ui.web.entity.RuleDefinitionEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * {@code rule_definition} 层的业务操作。
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RuleDefinitionService {

    private final RuleDefinitionDao ruleDao;
    private final FlowRuleRefDao refDao;
    private final RuleSuiteManager suiteManager;

    @Transactional
    public RuleDefinitionEntity create(RuleDefinitionEntity req) {
        validate(req);
        // 阻断1：预编译校验——把本次新增并入当前启用集合，确认新 RuleSuite 可构造再落库。
        java.util.List<RuleDefinitionEntity> prospective = new java.util.ArrayList<>(ruleDao.listActive());
        prospective.add(req);
        suiteManager.assertRulesBuildable(prospective);
        long id = ruleDao.insert(req);
        suiteManager.refreshAsyncAfterCommit();
        return ruleDao.findById(id);
    }

    @Transactional
    public RuleDefinitionEntity update(long id, RuleDefinitionEntity req) {
        RuleDefinitionEntity existing = requireRule(id);
        if (req.getVersion() == null) {
            throw new IllegalArgumentException("version is required for update (expected " + existing.getVersion() + ")");
        }
        req.setId(id);
        // status 只能经由启用/停用专用路径变更，PUT 不得携带 status 绕过引用检查。
        req.setStatus(existing.getStatus());
        validate(req);
        // 阻断1：把本次变更并入当前启用集合（按 id 替换），确认可构造再提交。
        java.util.List<RuleDefinitionEntity> prospective = new java.util.ArrayList<>(ruleDao.listActive());
        prospective.removeIf(r -> r.getId() != null && r.getId() == id);
        prospective.add(req);
        suiteManager.assertRulesBuildable(prospective);
        int rows = ruleDao.update(req);
        if (rows == 0) {
            throw new BusinessException(409, "rule updated by others, please retry (expected version=" + req.getVersion() + ")");
        }
        suiteManager.refreshAsyncAfterCommit();
        return ruleDao.findById(id);
    }

    @Transactional
    public RuleDefinitionEntity enable(long id, Long expectedVersion) {
        RuleDefinitionEntity existing = requireRule(id);
        long version = expectedVersion != null ? expectedVersion : existing.getVersion();
        // 启用前确认表达式可编译，避免坏规则进入运行态。
        java.util.List<RuleDefinitionEntity> prospective = new java.util.ArrayList<>(ruleDao.listActive());
        prospective.removeIf(r -> r.getId() != null && r.getId() == id);
        prospective.add(existing);
        suiteManager.assertRulesBuildable(prospective);
        int rows = ruleDao.enable(id, version);
        if (rows == 0) {
            throw new BusinessException(409, "rule updated by others, please retry");
        }
        suiteManager.refreshAsyncAfterCommit();
        return ruleDao.findById(id);
    }

    @Transactional
    public void disable(long id, Long expectedVersion) {
        RuleDefinitionEntity existing = requireRule(id);
        long version = expectedVersion != null ? expectedVersion : existing.getVersion();
        Set<Long> refs = refDao.activeFlowsReferencing(id);
        if (!refs.isEmpty()) {
            throw new BusinessException(409,
                    "rule " + id + " is still referenced by active flow(s): " + refs);
        }
        int rows = ruleDao.disable(id, version);
        if (rows == 0) {
            throw new BusinessException(409, "rule updated by others, please retry");
        }
        suiteManager.refreshAsyncAfterCommit();
    }

    public RuleDefinitionEntity findById(long id) {
        return ruleDao.findById(id);
    }

    /** 每条规则被多少个已生效流程引用（来自 flow_rule_ref，不下载全部流程树）。 */
    public java.util.Map<Long, Integer> refCounts() {
        return refDao.refCountsByActiveFlow();
    }

    public java.util.Map<String, Object> page(Integer status, Integer useType, String ruleKind,
                                               String keyword, int pageNumber, int pageSize) {
        int p = Math.max(pageNumber, 1);
        int s = Math.max(1, Math.min(pageSize, 200));
        int offset = (p - 1) * s;
        List<RuleDefinitionEntity> rows = ruleDao.list(status, useType, ruleKind, keyword, offset, s);
        int total = ruleDao.count(status, useType, ruleKind, keyword);
        return java.util.Map.of(
                "items", rows,
                "total", total,
                "pageNumber", p,
                "pageSize", s);
    }

    private RuleDefinitionEntity requireRule(long id) {
        RuleDefinitionEntity e = ruleDao.findById(id);
        if (e == null) {
            throw new BusinessException(404, "rule not found: " + id);
        }
        return e;
    }

    private void validate(RuleDefinitionEntity req) {
        if (req.getName() == null || req.getName().isBlank()) {
            throw new IllegalArgumentException("rule name is required");
        }
        if (req.getExpression() == null || req.getExpression().isBlank()) {
            throw new IllegalArgumentException("rule expression is required");
        }
        int useType = req.getUseType() == null ? 0 : req.getUseType();
        if (useType < 0 || useType > 2) {
            throw new IllegalArgumentException("use_type must be 0/1/2, got " + useType);
        }
        String kind = req.getRuleKind();
        if (kind == null || kind.isBlank()) {
            req.setRuleKind("condition");
        } else if (!"condition".equals(kind) && !"action".equals(kind)) {
            throw new IllegalArgumentException("rule_kind must be condition/action, got " + kind);
        }
    }
}
