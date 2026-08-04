package com.skyfalling.mosika.ui.web.service;

import com.skyfalling.mosika.ui.web.common.BusinessException;
import com.skyfalling.mosika.ui.web.dao.FlowRuleRefDao;
import com.skyfalling.mosika.ui.web.dao.RuleDefinitionDao;
import com.skyfalling.mosika.ui.web.entity.RuleDefinitionEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * {@code rule_definition} 层的业务操作。
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
@Service
@RequiredArgsConstructor
public class RuleDefinitionService {

    private final RuleDefinitionDao ruleDao;
    private final FlowRuleRefDao refDao;
    private final RuleSuiteManager suiteManager;

    @Transactional
    public RuleDefinitionEntity create(RuleDefinitionEntity req) {
        validate(req);
        // 新规则默认启用；先在当前事务中插入以取得真实 id，再预编译。失败会整体回滚。
        req.setStatus(1);
        long id = ruleDao.insert(req);
        RuleDefinitionEntity inserted = ruleDao.findById(id);
        java.util.List<RuleDefinitionEntity> prospective = new java.util.ArrayList<>(ruleDao.listActive());
        suiteManager.assertRulesBuildable(prospective);
        suiteManager.refreshAfterCommit();
        return inserted;
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
        if (existing.getStatus() != null && existing.getStatus() == 1) {
            suiteManager.refreshAfterCommit();
        }
        return ruleDao.findById(id);
    }

    @Transactional
    public RuleDefinitionEntity enable(long id, long expectedVersion) {
        RuleDefinitionEntity existing = requireRule(id);
        // 启用前确认表达式可编译，避免坏规则进入运行态。
        java.util.List<RuleDefinitionEntity> prospective = new java.util.ArrayList<>(ruleDao.listActive());
        prospective.removeIf(r -> r.getId() != null && r.getId() == id);
        prospective.add(existing);
        suiteManager.assertRulesBuildable(prospective);
        int rows = ruleDao.enable(id, expectedVersion);
        if (rows == 0) {
            throw new BusinessException(409, "rule updated by others, please retry");
        }
        suiteManager.refreshAfterCommit();
        return ruleDao.findById(id);
    }

    @Transactional
    public void disable(long id, long expectedVersion) {
        requireRule(id);
        // 停用 = 下架：从可选规则池移除、禁止被新场景引用；已引用它的已生效场景仍会正常运行
        // （RuleSuiteManager 会继续装载被已生效场景引用的规则，即便其已停用）。故此处不因存量引用而拦截。
        int rows = ruleDao.disable(id, expectedVersion);
        if (rows == 0) {
            throw new BusinessException(409, "rule updated by others, please retry");
        }
        suiteManager.refreshAfterCommit();
    }

    public RuleDefinitionEntity findById(long id) {
        return ruleDao.findById(id);
    }

    /** 每条规则被多少个已生效流程引用（来自 flow_rule_ref，不下载全部流程树）。 */
    public java.util.Map<Long, Integer> refCounts() {
        return refDao.refCountsByActiveFlow();
    }

    /** 画布下拉框使用的轻量启用规则索引，不返回表达式正文。 */
    public java.util.List<java.util.Map<String, Object>> activeReferences() {
        return ruleDao.listActive().stream().map(rule -> {
            java.util.Map<String, Object> reference = new java.util.LinkedHashMap<>();
            reference.put("id", rule.getId());
            reference.put("name", rule.getName());
            reference.put("description", rule.getDescription());
            reference.put("ruleKind", rule.getRuleKind());
            reference.put("useType", rule.getUseType());
            return reference;
        }).toList();
    }

    public java.util.Map<String, Object> page(Integer status, Integer useType, String ruleKind,
                                               String keyword, int pageNumber, int pageSize) {
        int p = Math.max(pageNumber, 1);
        int s = Math.max(1, Math.min(pageSize, 200));
        long offset = (long) (p - 1) * s;
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
