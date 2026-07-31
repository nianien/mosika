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
        long id = ruleDao.insert(req);
        suiteManager.refreshAsyncAfterCommit();
        return ruleDao.findById(id);
    }

    @Transactional
    public RuleDefinitionEntity update(long id, RuleDefinitionEntity req) {
        RuleDefinitionEntity existing = requireRule(id);
        req.setId(id);
        req.setVersion(existing.getVersion());
        validate(req);
        int rows = ruleDao.update(req);
        if (rows == 0) {
            throw new BusinessException(409, "rule updated by others, please retry (version=" + existing.getVersion() + ")");
        }
        suiteManager.refreshAsyncAfterCommit();
        return ruleDao.findById(id);
    }

    @Transactional
    public void disable(long id) {
        RuleDefinitionEntity existing = requireRule(id);
        Set<Long> refs = refDao.activeFlowsReferencing(id);
        if (!refs.isEmpty()) {
            throw new BusinessException(409,
                    "rule " + id + " is still referenced by active flow(s): " + refs);
        }
        int rows = ruleDao.disable(id, existing.getVersion());
        if (rows == 0) {
            throw new BusinessException(409, "rule updated by others, please retry");
        }
        suiteManager.refreshAsyncAfterCommit();
    }

    public RuleDefinitionEntity findById(long id) {
        return ruleDao.findById(id);
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
