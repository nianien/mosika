package com.skyfalling.mosika.ui.web.service;

import com.skyfalling.mosika.ui.web.common.BusinessException;
import com.skyfalling.mosika.ui.web.dao.RuleNamespaceDao;
import com.skyfalling.mosika.ui.web.entity.RuleNamespaceEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 命名空间业务服务 */
@Service
@RequiredArgsConstructor
public class RuleNamespaceService {

    /** 命名空间持久化访问对象 */
    private final RuleNamespaceDao namespaceDao;

    /** 命名空间运行态规则套件管理器 */
    private final RuleSuiteManager suiteManager;

    /** 查询全部命名空间 */
    public List<Map<String, Object>> list() {
        return namespaceDao.list().stream().map(namespace -> {
            RuleNamespaceDao.Usage usage = namespaceDao.countUsage(namespace.getId());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("code", namespace.getCode());
            item.put("name", namespace.getName());
            item.put("description", namespace.getDescription());
            item.put("status", namespace.getStatus());
            item.put("createdAt", namespace.getCreatedAt());
            item.put("updatedAt", namespace.getUpdatedAt());
            item.put("ruleCount", usage.ruleCount());
            item.put("flowCount", usage.flowCount());
            item.put("udfCount", usage.udfCount());
            return item;
        }).toList();
    }

    /** 创建命名空间 */
    @Transactional
    public RuleNamespaceEntity create(RuleNamespaceEntity request) {
        if (request.getCode() == null
                || !request.getCode().matches("[A-Za-z][A-Za-z0-9_.-]{0,63}")) {
            throw new IllegalArgumentException("namespace code format is invalid");
        }
        if (request.getName() == null || request.getName().isBlank()) {
            throw new IllegalArgumentException("namespace name is required");
        }
        try {
            namespaceDao.insert(request);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(409, "namespace already exists: " + request.getCode());
        }
        suiteManager.refreshAfterCommit();
        return namespaceDao.findByCode(request.getCode());
    }

    /** 更新命名空间名称和说明 */
    @Transactional
    public RuleNamespaceEntity update(String code, String name, String description) {
        requireNamespace(code);
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("namespace name is required");
        }
        namespaceDao.update(code, name, description);
        return namespaceDao.findByCode(code);
    }

    /** 停用空命名空间并移除其运行态套件 */
    @Transactional
    public RuleNamespaceEntity disable(String code) {
        if ("default".equals(code)) {
            throw new BusinessException(400, "default namespace cannot be disabled");
        }
        RuleNamespaceEntity namespace = requireNamespace(code);
        RuleNamespaceDao.Usage usage = namespaceDao.countUsage(namespace.getId());
        if (usage.ruleCount() != 0 || usage.flowCount() != 0 || usage.udfCount() != 0) {
            throw new BusinessException(409,
                    "namespace is not empty: rules=" + usage.ruleCount()
                            + ", flows=" + usage.flowCount()
                            + ", udfs=" + usage.udfCount());
        }
        namespaceDao.updateStatus(code, 0);
        suiteManager.refreshAfterCommit();
        return namespaceDao.findByCode(code);
    }

    /** 启用命名空间并装配其运行态套件 */
    @Transactional
    public RuleNamespaceEntity enable(String code) {
        requireNamespace(code);
        namespaceDao.updateStatus(code, 1);
        suiteManager.refreshAfterCommit();
        return namespaceDao.findByCode(code);
    }

    /** 查询必须存在的命名空间 */
    private RuleNamespaceEntity requireNamespace(String code) {
        RuleNamespaceEntity namespace = namespaceDao.findByCode(code);
        if (namespace == null) {
            throw new BusinessException(404, "namespace not found: " + code);
        }
        return namespace;
    }
}
