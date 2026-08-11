package com.skyfalling.mosika.ui.web.controller;

import com.skyfalling.mosika.ui.web.common.ApiResponse;
import com.skyfalling.mosika.ui.web.common.BusinessException;
import com.skyfalling.mosika.ui.web.entity.AtomicRuleEntity;
import com.skyfalling.mosika.ui.web.service.AtomicRuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 原子规则管理 REST 接口
 * <p>
 * 路径中的规则标识统一使用由数据库主键派生的 {@code ruleId}
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
@RestController
@RequestMapping("/api/rules")
@RequiredArgsConstructor
public class AtomicRuleController {

    /** 原子规则业务服务 */
    private final AtomicRuleService service;

    /** 创建并默认启用原子规则 */
    @PostMapping
    public ApiResponse<AtomicRuleEntity> create(@RequestBody AtomicRuleEntity request) {
        return ApiResponse.ok(service.create(request));
    }

    /** 按 ruleId 和乐观锁更新原子规则 */
    @PutMapping("/{ruleId}")
    public ApiResponse<AtomicRuleEntity> update(@PathVariable String ruleId,
                                                @RequestBody AtomicRuleEntity request) {
        return ApiResponse.ok(service.update(ruleId, request));
    }

    /** 按 ruleId 和乐观锁停用原子规则 */
    @DeleteMapping("/{ruleId}")
    public ApiResponse<Void> disable(@PathVariable String ruleId, @RequestParam long version) {
        service.disable(ruleId, version);
        return ApiResponse.ok();
    }

    /** 按 ruleId 和乐观锁启用原子规则 */
    @PostMapping("/{ruleId}/enable")
    public ApiResponse<AtomicRuleEntity> enable(@PathVariable String ruleId,
                                                @RequestParam long version) {
        return ApiResponse.ok(service.enable(ruleId, version));
    }

    /** 查询每条原子规则被运行态规则流闭包直接引用的次数 */
    @GetMapping("/ref-counts")
    public ApiResponse<Map<String, Integer>> refCounts(@RequestParam(required = false) String namespace) {
        return ApiResponse.ok(service.refCounts(namespace));
    }

    /** 查询指定命名空间内可引用的启用原子规则 */
    @GetMapping("/references")
    public ApiResponse<List<Map<String, Object>>> references(
            @RequestParam(required = false) String namespace) {
        return ApiResponse.ok(service.activeReferences(namespace));
    }

    /** 按 ruleId 查询原子规则 */
    @GetMapping("/{ruleId}")
    public ApiResponse<AtomicRuleEntity> get(@PathVariable String ruleId) {
        AtomicRuleEntity entity = service.findByRuleId(ruleId);
        if (entity == null) {
            throw new BusinessException(404, "rule not found: " + ruleId);
        }
        return ApiResponse.ok(entity);
    }

    /** 按状态、分类、命名空间和关键字分页查询原子规则 */
    @GetMapping
    public ApiResponse<Map<String, Object>> list(@RequestParam(required = false) Integer status,
                                                 @RequestParam(required = false) String kind,
                                                 @RequestParam(required = false) String namespace,
                                                 @RequestParam(required = false) String keyword,
                                                 @RequestParam(defaultValue = "1") int pageNumber,
                                                 @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(service.page(status, kind, namespace, keyword, pageNumber, pageSize));
    }
}
