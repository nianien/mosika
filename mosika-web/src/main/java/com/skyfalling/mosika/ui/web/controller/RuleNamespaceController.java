package com.skyfalling.mosika.ui.web.controller;

import com.skyfalling.mosika.ui.web.common.ApiResponse;
import com.skyfalling.mosika.ui.web.entity.RuleNamespaceEntity;
import com.skyfalling.mosika.ui.web.service.RuleNamespaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 命名空间管理 REST 接口 */
@RestController
@RequestMapping("/api/namespaces")
@RequiredArgsConstructor
public class RuleNamespaceController {

    /** 命名空间业务服务 */
    private final RuleNamespaceService service;

    /** 查询全部命名空间 */
    @GetMapping
    public ApiResponse<List<RuleNamespaceEntity>> list() {
        return ApiResponse.ok(service.list());
    }

    /** 创建命名空间 */
    @PostMapping
    public ApiResponse<RuleNamespaceEntity> create(@RequestBody RuleNamespaceEntity request) {
        return ApiResponse.ok(service.create(request));
    }
}
