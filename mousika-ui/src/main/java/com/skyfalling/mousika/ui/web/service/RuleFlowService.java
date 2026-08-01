package com.skyfalling.mousika.ui.web.service;

import com.skyfalling.mousika.ui.web.common.BusinessException;
import com.skyfalling.mousika.ui.web.dao.FlowRuleRefDao;
import com.skyfalling.mousika.ui.web.dao.RuleDefinitionDao;
import com.skyfalling.mousika.ui.web.dao.RuleFlowDao;
import com.skyfalling.mousika.ui.web.entity.RuleDefinitionEntity;
import com.skyfalling.mousika.ui.web.entity.RuleFlowEntity;
import com.skyfalling.mousika.ui.web.service.RuleTreeCompiler.CompileResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@code rule_flow} 层的业务操作：保存前先由 {@link RuleTreeCompiler} 校验/规范化 UI 树，
 * 校验通过后落库、重建 {@code flow_rule_ref}，并触发 {@link RuleSuiteManager} 重建。
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RuleFlowService {

    private final RuleFlowDao flowDao;
    private final RuleDefinitionDao ruleDao;
    private final FlowRuleRefDao refDao;
    private final RuleSuiteManager suiteManager;

    @Transactional
    public RuleFlowEntity create(RuleFlowEntity req) {
        basicCheck(req);
        CompileResult compiled = RuleTreeCompiler.compile(req.getRuleTree());
        Set<Long> refIds = verifyReferences(compiled.getReferenced());
        req.setRuleTree(compiled.getCanonicalJson());
        long id = flowDao.insert(req);
        refDao.replaceForFlow(id, refIds);
        suiteManager.refreshAsyncAfterCommit();
        return flowDao.findById(id);
    }

    @Transactional
    public RuleFlowEntity update(long id, RuleFlowEntity req) {
        RuleFlowEntity existing = requireFlow(id);
        if (req.getVersion() == null) {
            throw new IllegalArgumentException("version is required for update (expected " + existing.getVersion() + ")");
        }
        basicCheck(req);
        CompileResult compiled = RuleTreeCompiler.compile(req.getRuleTree());
        Set<Long> refIds = verifyReferences(compiled.getReferenced());
        req.setId(id);
        req.setRuleTree(compiled.getCanonicalJson());
        int rows = flowDao.update(req);
        if (rows == 0) {
            throw new BusinessException(409, "flow updated by others, please retry (expected version=" + req.getVersion() + ")");
        }
        refDao.replaceForFlow(id, refIds);
        suiteManager.refreshAsyncAfterCommit();
        return flowDao.findById(id);
    }

    @Transactional
    public void disable(long id, Long expectedVersion) {
        RuleFlowEntity existing = requireFlow(id);
        long version = expectedVersion != null ? expectedVersion : existing.getVersion();
        int rows = flowDao.disable(id, version);
        if (rows == 0) {
            throw new BusinessException(409, "flow updated by others, please retry");
        }
        // 停用不清引用表，仅在启用集合中"生效引用"过滤时通过 f.status=1 剔除
        suiteManager.refreshAsyncAfterCommit();
    }

    public RuleFlowEntity findById(long id) {
        return flowDao.findById(id);
    }

    public java.util.Map<String, Object> page(Integer status, String keyword,
                                              int pageNumber, int pageSize) {
        int p = Math.max(pageNumber, 1);
        int s = Math.max(1, Math.min(pageSize, 200));
        int offset = (p - 1) * s;
        List<RuleFlowEntity> rows = flowDao.list(status, keyword, offset, s);
        int total = flowDao.count(status, keyword);
        return java.util.Map.of(
                "items", rows,
                "total", total,
                "pageNumber", p,
                "pageSize", s);
    }

    /** 试算：校验并回吐规范 JSON、DSL、引用集合，不落库；供前端"保存前校验"使用。 */
    public java.util.Map<String, Object> dryRun(String ruleTree) {
        CompileResult compiled = RuleTreeCompiler.compile(ruleTree);
        Set<Long> refIds = verifyReferences(compiled.getReferenced());
        return java.util.Map.of(
                "canonicalJson", compiled.getCanonicalJson(),
                "dsl", compiled.getDsl(),
                "referencedRuleIds", refIds);
    }

    private RuleFlowEntity requireFlow(long id) {
        RuleFlowEntity e = flowDao.findById(id);
        if (e == null) {
            throw new BusinessException(404, "flow not found: " + id);
        }
        return e;
    }

    private void basicCheck(RuleFlowEntity req) {
        if (req.getName() == null || req.getName().isBlank()) {
            throw new IllegalArgumentException("flow name is required");
        }
        if (req.getRuleTree() == null || req.getRuleTree().isBlank()) {
            throw new IllegalArgumentException("ruleTree is required");
        }
    }

    /**
     * 引用完整性：数字型 id 必须命中启用中的原子规则；非数字 id（如 true/false/∅）忽略。
     * 返回本次 flow 需要写入 {@code flow_rule_ref} 的数字型 rule id 集合。
     */
    private Set<Long> verifyReferences(Set<String> collected) {
        Set<Long> numeric = new HashSet<>();
        Set<String> nonNumeric = new HashSet<>();
        for (String s : collected) {
            try {
                numeric.add(Long.parseLong(s));
            } catch (NumberFormatException nfe) {
                nonNumeric.add(s);
            }
        }
        if (!nonNumeric.isEmpty()) {
            log.debug("skipping non-numeric refs (core constants / UDFs): {}", nonNumeric);
        }
        if (numeric.isEmpty()) {
            return numeric;
        }
        List<RuleDefinitionEntity> found = ruleDao.findByIds(numeric);
        Set<Long> foundActive = found.stream()
                .filter(r -> r.getStatus() != null && r.getStatus() == 1)
                .map(RuleDefinitionEntity::getId)
                .collect(Collectors.toSet());
        Set<Long> missing = new HashSet<>(numeric);
        missing.removeAll(foundActive);
        if (!missing.isEmpty()) {
            throw new BusinessException(400,
                    "ruleTree references unknown or disabled rule ids: " + missing);
        }
        return foundActive;
    }
}
