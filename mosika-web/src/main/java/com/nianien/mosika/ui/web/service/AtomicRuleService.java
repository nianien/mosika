package com.nianien.mosika.ui.web.service;

import com.nianien.mosika.ui.web.common.BusinessException;
import com.nianien.mosika.ui.web.dao.AtomicRuleDao;
import com.nianien.mosika.ui.web.dao.FlowReferenceDao;
import com.nianien.mosika.ui.web.dao.RuleNamespaceDao;
import com.nianien.mosika.ui.web.entity.AtomicRuleEntity;
import com.nianien.mosika.ui.web.entity.RuleNamespaceEntity;
import com.nianien.mosika.utils.JsonUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    public Map<String, Integer> refCounts(String namespace) {
        return referenceDao.atomicRefCountsByActiveFlow(requireNamespace(namespace).getId());
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
            reference.put("expression", rule.getExpression());
            reference.put("params", parseParams(rule.getParams()));
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
        request.setParams(validateParams(request.getParams()));
    }

    /** 允许的参数类型 */
    private static final Set<String> PARAM_TYPES = Set.of("string", "number", "boolean", "enum");

    /**
     * 校验并规范化参数模板 JSON
     * <p>
     * 空值归一为 {@code []}；否则必须是 JSON 数组，每个元素声明非空 {@code name} 与合法 {@code type}，
     * {@code type=enum} 时必须带非空 {@code options}。校验通过后原样返回，不改写内部结构（原始 JSON 透传）
     */
    static String validateParams(String params) {
        if (params == null || params.isBlank()) {
            return "[]";
        }
        List<?> defs;
        try {
            defs = JsonUtils.toList(params, Object.class);
        } catch (Exception e) {
            // JsonUtils 以 @SneakyThrows 透传 Jackson 的受检 IOException（解析/类型不匹配），
            // 故必须捕获 Exception 而非仅 RuntimeException，否则非法 JSON 会绕过本层校验
            throw new IllegalArgumentException("params must be a JSON array: " + e.getMessage(), e);
        }
        Set<String> names = new java.util.HashSet<>();
        for (Object def : defs) {
            if (!(def instanceof Map<?, ?> param)) {
                throw new IllegalArgumentException("each param must be a JSON object");
            }
            Object name = param.get("name");
            if (!(name instanceof String s) || s.isBlank()) {
                throw new IllegalArgumentException("param name is required");
            }
            if (!names.add(s)) {
                throw new IllegalArgumentException("duplicate param name: " + s);
            }
            Object type = param.get("type");
            if (!(type instanceof String t) || !PARAM_TYPES.contains(t)) {
                throw new IllegalArgumentException(
                        "param type must be one of " + PARAM_TYPES + ", got " + type);
            }
            if ("enum".equals(type)) {
                Object options = param.get("options");
                if (!(options instanceof List<?> opts) || opts.isEmpty()) {
                    throw new IllegalArgumentException("enum param '" + s + "' requires non-empty options");
                }
            }
        }
        return params;
    }

    /** 参数模板 JSON 解析为结构化列表，供画布直接消费；空或非法归一为空列表 */
    static List<?> parseParams(String params) {
        if (params == null || params.isBlank()) {
            return List.of();
        }
        try {
            return JsonUtils.toList(params, Object.class);
        } catch (Exception e) {
            // 同 validateParams：JsonUtils 透传受检解析异常，此处对非法 JSON 静默回退空列表
            return List.of();
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
