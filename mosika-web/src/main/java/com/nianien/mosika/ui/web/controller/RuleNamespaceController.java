package com.nianien.mosika.ui.web.controller;

import com.nianien.mosika.ui.web.common.ApiResponse;
import com.nianien.mosika.ui.web.entity.RuleNamespaceEntity;
import com.nianien.mosika.ui.web.service.RuleNamespaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 命名空间管理 REST 接口 */
@RestController
@RequestMapping("/api/namespaces")
@RequiredArgsConstructor
public class RuleNamespaceController {

    /** 命名空间业务服务 */
    private final RuleNamespaceService service;

    /** 查询全部命名空间 */
    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list() {
        return ApiResponse.ok(service.list());
    }

    /** 创建命名空间 */
    @PostMapping
    public ApiResponse<RuleNamespaceEntity> create(@RequestBody RuleNamespaceEntity request) {
        return ApiResponse.ok(service.create(request));
    }

    /** 更新命名空间名称和说明 */
    @PutMapping("/{code}")
    public ApiResponse<RuleNamespaceEntity> update(@PathVariable String code,
                                                   @RequestBody RuleNamespaceEntity request) {
        return ApiResponse.ok(service.update(code, request.getName(), request.getDescription()));
    }

    /** 停用空命名空间 */
    @PostMapping("/{code}/disable")
    public ApiResponse<RuleNamespaceEntity> disable(@PathVariable String code) {
        return ApiResponse.ok(service.disable(code));
    }

    /** 启用命名空间 */
    @PostMapping("/{code}/enable")
    public ApiResponse<RuleNamespaceEntity> enable(@PathVariable String code) {
        return ApiResponse.ok(service.enable(code));
    }
}
