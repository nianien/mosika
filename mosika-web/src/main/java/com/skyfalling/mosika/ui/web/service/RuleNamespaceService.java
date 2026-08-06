package com.skyfalling.mosika.ui.web.service;

import com.skyfalling.mosika.ui.web.common.BusinessException;
import com.skyfalling.mosika.ui.web.dao.RuleNamespaceDao;
import com.skyfalling.mosika.ui.web.entity.RuleNamespaceEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 命名空间业务服务 */
@Service
@RequiredArgsConstructor
public class RuleNamespaceService {

    /** 命名空间持久化访问对象 */
    private final RuleNamespaceDao namespaceDao;

    /** 查询全部命名空间 */
    public List<RuleNamespaceEntity> list() {
        return namespaceDao.list();
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
        return namespaceDao.findByCode(request.getCode());
    }
}
