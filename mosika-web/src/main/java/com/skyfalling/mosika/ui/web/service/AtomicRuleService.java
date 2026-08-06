package com.skyfalling.mosika.ui.web.service;

import com.skyfalling.mosika.ui.web.common.BusinessException;
import com.skyfalling.mosika.ui.web.dao.AtomicRuleDao;
import com.skyfalling.mosika.ui.web.dao.FlowReferenceDao;
import com.skyfalling.mosika.ui.web.dao.RuleNamespaceDao;
import com.skyfalling.mosika.ui.web.entity.AtomicRuleEntity;
import com.skyfalling.mosika.ui.web.entity.RuleNamespaceEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 原子规则业务服务
 * <p>
 * 负责命名空间归属、乐观锁写入、派生 {@code ruleId} 和运行态套件刷新
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
@Service
@RequiredArgsConstructor
public class AtomicRuleService {

    /** 默认命名空间编码 */
    public static final String DEFAULT_NAMESPACE = "default";

    /** 原子规则持久化访问对象 */
    private final AtomicRuleDao ruleDao;

    /** 规则流引用索引 */
    private final FlowReferenceDao referenceDao;

    /** 命名空间持久化访问对象 */
    private final RuleNamespaceDao namespaceDao;

    /** 全局运行态规则套件管理器 */
    private final RuleSuiteManager suiteManager;

    /** 创建并默认启用原子规则 */
    @Transactional
    public AtomicRuleEntity create(AtomicRuleEntity request) {
        validate(request);
        RuleNamespaceEntity namespace = requireNamespace(request.getNamespace());
        request.setNamespaceId(namespace.getId());
        request.setNamespace(namespace.getCode());
        request.setStatus(1);
        request.setId(ruleDao.insert(request));
        AtomicRuleEntity inserted = ruleDao.findByRuleId(request.getRuleId());
        suiteManager.refreshAfterCommit();
        return inserted;
    }

    /** 按乐观锁更新原子规则正文和元数据 */
    @Transactional
    public AtomicRuleEntity update(String ruleId, AtomicRuleEntity request) {
        AtomicRuleEntity existing = requireRule(ruleId);
        requireVersion(request, existing, "update");
        validate(request);
        assertNamespaceUnchanged(request.getNamespace(), existing.getNamespace());
        request.setId(existing.getId());
        request.setNamespaceId(existing.getNamespaceId());
        request.setNamespace(existing.getNamespace());
        request.setStatus(existing.getStatus());
        boolean loaded = existing.getStatus() != null && existing.getStatus() == 1
                || referenceDao.runtimeRuleIds().contains(existing.getId());
        if (ruleDao.update(ruleId, request) == 0) {
            throw new BusinessException(409,
                    "rule updated by others, please retry (expected version=" + request.getVersion() + ")");
        }
        if (loaded) {
            suiteManager.refreshAfterCommit();
        } else {
            suiteManager.assertAtomicRuleBuildable(request);
        }
        return ruleDao.findByRuleId(ruleId);
    }

    /** 启用原子规则 */
    @Transactional
    public AtomicRuleEntity enable(String ruleId, long expectedVersion) {
        requireRule(ruleId);
        if (ruleDao.enable(ruleId, expectedVersion) == 0) {
            throw new BusinessException(409, "rule updated by others, please retry");
        }
        suiteManager.refreshAfterCommit();
        return ruleDao.findByRuleId(ruleId);
    }

    /** 停用原子规则 */
    @Transactional
    public void disable(String ruleId, long expectedVersion) {
        requireRule(ruleId);
        if (ruleDao.disable(ruleId, expectedVersion) == 0) {
            throw new BusinessException(409, "rule updated by others, please retry");
        }
        suiteManager.refreshAfterCommit();
    }

    /** 按 ruleId 查询原子规则 */
    public AtomicRuleEntity findByRuleId(String ruleId) {
        return ruleDao.findByRuleId(ruleId);
    }

    /** 查询原子规则被运行态规则流闭包直接引用的次数 */
    public Map<String, Integer> refCounts() {
        return referenceDao.atomicRefCountsByActiveFlow();
    }

    /** 查询规则编辑器使用的轻量启用原子规则索引 */
    public List<Map<String, Object>> activeReferences(String namespace) {
        return ruleDao.listActive(normalizeNamespace(namespace)).stream().map(rule -> {
            Map<String, Object> reference = new LinkedHashMap<>();
            reference.put("ruleId", rule.getRuleId());
            reference.put("name", rule.getName());
            reference.put("description", rule.getDescription());
            reference.put("kind", rule.getKind());
            reference.put("namespace", rule.getNamespace());
            return reference;
        }).toList();
    }

    /** 分页查询原子规则 */
    public Map<String, Object> page(Integer status, String kind, String namespace,
                                    String keyword, int pageNumber, int pageSize) {
        if (kind != null && !kind.isBlank()) {
            validateKind(kind);
        }
        int page = Math.max(pageNumber, 1);
        int size = Math.max(1, Math.min(pageSize, 200));
        long offset = (long) (page - 1) * size;
        List<AtomicRuleEntity> rows = ruleDao.list(status, kind, namespace, keyword, offset, size);
        int total = ruleDao.count(status, kind, namespace, keyword);
        return Map.of("items", rows, "total", total, "pageNumber", page, "pageSize", size);
    }

    /** 查询必须存在的原子规则 */
    private AtomicRuleEntity requireRule(String ruleId) {
        AtomicRuleEntity entity = ruleDao.findByRuleId(ruleId);
        if (entity == null) {
            throw new BusinessException(404, "rule not found: " + ruleId);
        }
        return entity;
    }

    /** 查询必须存在且启用的命名空间 */
    private RuleNamespaceEntity requireNamespace(String code) {
        String normalized = normalizeNamespace(code);
        RuleNamespaceEntity namespace = namespaceDao.findByCode(normalized);
        if (namespace == null || namespace.getStatus() == null || namespace.getStatus() != 1) {
            throw new BusinessException(400, "unknown or disabled namespace: " + normalized);
        }
        return namespace;
    }

    /** 校验原子规则请求 */
    private void validate(AtomicRuleEntity request) {
        if (request.getName() == null || request.getName().isBlank()) {
            throw new IllegalArgumentException("rule name is required");
        }
        if (request.getExpression() == null || request.getExpression().isBlank()) {
            throw new IllegalArgumentException("rule expression is required");
        }
        if (request.getKind() == null || request.getKind().isBlank()) {
            request.setKind("condition");
        } else {
            validateKind(request.getKind());
        }
    }

    /** 校验规则分类 */
    private static void validateKind(String kind) {
        if (!"condition".equals(kind) && !"action".equals(kind)) {
            throw new IllegalArgumentException("kind must be condition/action, got " + kind);
        }
    }

    /** 校验请求版本 */
    private static void requireVersion(AtomicRuleEntity request, AtomicRuleEntity existing, String operation) {
        if (request.getVersion() == null) {
            throw new IllegalArgumentException(
                    "version is required for " + operation + " (expected " + existing.getVersion() + ")");
        }
    }

    /** 原子规则创建后不允许改变命名空间 */
    private static void assertNamespaceUnchanged(String requested, String existing) {
        if (requested != null && !requested.isBlank() && !existing.equals(requested)) {
            throw new BusinessException(400, "namespace cannot be changed after creation");
        }
    }

    /** 空命名空间归一为默认命名空间 */
    static String normalizeNamespace(String namespace) {
        return namespace == null || namespace.isBlank() ? DEFAULT_NAMESPACE : namespace.trim();
    }
}
