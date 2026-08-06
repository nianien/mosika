package com.skyfalling.mosika.ui.web.controller;

import com.skyfalling.mosika.ui.web.common.ApiResponse;
import com.skyfalling.mosika.ui.web.common.BusinessException;
import com.skyfalling.mosika.ui.web.entity.RuleFlowEntity;
import com.skyfalling.mosika.ui.web.service.RuleFlowService;
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
 * 规则流管理 REST 接口
 * <p>
 * 路径中的规则流标识统一使用由数据库主键派生的 {@code flowId}
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
@RestController
@RequestMapping("/api/flows")
@RequiredArgsConstructor
public class RuleFlowController {

    /** 规则流业务服务 */
    private final RuleFlowService service;

    /** 创建规则流草稿 */
    @PostMapping
    public ApiResponse<RuleFlowEntity> create(@RequestBody RuleFlowEntity request) {
        return ApiResponse.ok(service.create(request));
    }

    /** 保存规则流草稿 */
    @PutMapping("/{flowId}")
    public ApiResponse<RuleFlowEntity> update(@PathVariable String flowId,
                                              @RequestBody RuleFlowEntity request) {
        return ApiResponse.ok(service.saveDraft(flowId, request));
    }

    /** 全量校验并发布规则流 */
    @PostMapping("/{flowId}/publish")
    public ApiResponse<RuleFlowEntity> publish(@PathVariable String flowId,
                                               @RequestBody RuleFlowEntity request) {
        return ApiResponse.ok(service.publish(flowId, request));
    }

    /** 仅编辑规则流名称和描述 */
    @PutMapping("/{flowId}/meta")
    public ApiResponse<RuleFlowEntity> updateMeta(@PathVariable String flowId,
                                                  @RequestBody RuleFlowEntity request) {
        return ApiResponse.ok(service.updateMeta(flowId, request));
    }

    /** 停用规则流 */
    @DeleteMapping("/{flowId}")
    public ApiResponse<Void> disable(@PathVariable String flowId, @RequestParam long version) {
        service.disable(flowId, version);
        return ApiResponse.ok();
    }

    /** 按 flowId 查询规则流 */
    @GetMapping("/{flowId}")
    public ApiResponse<RuleFlowEntity> get(@PathVariable String flowId) {
        RuleFlowEntity entity = service.findByFlowId(flowId);
        if (entity == null) {
            throw new BusinessException(404, "flow not found: " + flowId);
        }
        return ApiResponse.ok(entity);
    }

    /** 分页查询规则流 */
    @GetMapping
    public ApiResponse<Map<String, Object>> list(@RequestParam(required = false) Integer status,
                                                 @RequestParam(required = false) String namespace,
                                                 @RequestParam(required = false) String keyword,
                                                 @RequestParam(defaultValue = "1") int pageNumber,
                                                 @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(service.page(status, namespace, keyword, pageNumber, pageSize));
    }

    /** 查询指定命名空间内可引用的已发布规则流 */
    @GetMapping("/references/active")
    public ApiResponse<List<Map<String, Object>>> references(
            @RequestParam(required = false) String namespace) {
        return ApiResponse.ok(service.activeReferences(namespace));
    }

    /** 保存前校验规则树 */
    @PostMapping("/validate")
    public ApiResponse<Map<String, Object>> validate(@RequestBody RuleFlowEntity request) {
        return ApiResponse.ok(service.dryRun(request.getRuleTree(), request.getNamespace()));
    }
}
