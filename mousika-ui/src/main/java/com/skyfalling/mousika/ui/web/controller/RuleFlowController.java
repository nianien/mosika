package com.skyfalling.mousika.ui.web.controller;

import com.skyfalling.mousika.ui.web.common.ApiResponse;
import com.skyfalling.mousika.ui.web.entity.RuleFlowEntity;
import com.skyfalling.mousika.ui.web.service.RuleFlowService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 规则流（RuleFlow）CRUD 接口。
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
@RestController
@RequestMapping("/api/flows")
@RequiredArgsConstructor
public class RuleFlowController {

    private final RuleFlowService service;

    @PostMapping
    public ApiResponse<RuleFlowEntity> create(@RequestBody RuleFlowEntity req) {
        return ApiResponse.ok(service.create(req));
    }

    @PutMapping("/{id}")
    public ApiResponse<RuleFlowEntity> update(@PathVariable long id,
                                              @RequestBody RuleFlowEntity req) {
        return ApiResponse.ok(service.update(id, req));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> disable(@PathVariable long id) {
        service.disable(id);
        return ApiResponse.ok();
    }

    @GetMapping("/{id}")
    public ApiResponse<RuleFlowEntity> get(@PathVariable long id) {
        RuleFlowEntity e = service.findById(id);
        if (e == null) {
            return ApiResponse.fail(404, "flow not found: " + id);
        }
        return ApiResponse.ok(e);
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> list(@RequestParam(required = false) Integer status,
                                                 @RequestParam(required = false) String keyword,
                                                 @RequestParam(defaultValue = "1") int pageNumber,
                                                 @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(service.page(status, keyword, pageNumber, pageSize));
    }

    /** 保存前校验：反序列化 UI 树、结构校验、编译到 DSL、回吐规范 JSON 与引用集合。 */
    @PostMapping("/validate")
    public ApiResponse<Map<String, Object>> validate(@RequestBody RuleFlowEntity req) {
        return ApiResponse.ok(service.dryRun(req.getRuleTree()));
    }
}
