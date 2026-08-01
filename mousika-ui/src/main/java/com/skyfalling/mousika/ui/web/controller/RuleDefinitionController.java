package com.skyfalling.mousika.ui.web.controller;

import com.skyfalling.mousika.ui.web.common.ApiResponse;
import com.skyfalling.mousika.ui.web.entity.RuleDefinitionEntity;
import com.skyfalling.mousika.ui.web.service.RuleDefinitionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 规则（原子/决策表）CRUD 接口。
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
@RestController
@RequestMapping("/api/rules")
@RequiredArgsConstructor
public class RuleDefinitionController {

    private final RuleDefinitionService service;

    @PostMapping
    public ApiResponse<RuleDefinitionEntity> create(@RequestBody RuleDefinitionEntity req) {
        return ApiResponse.ok(service.create(req));
    }

    @PutMapping("/{id}")
    public ApiResponse<RuleDefinitionEntity> update(@PathVariable long id,
                                                    @RequestBody RuleDefinitionEntity req) {
        return ApiResponse.ok(service.update(id, req));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> disable(@PathVariable long id,
                                     @RequestParam(required = false) Long version) {
        service.disable(id, version);
        return ApiResponse.ok();
    }

    @GetMapping("/{id}")
    public ApiResponse<RuleDefinitionEntity> get(@PathVariable long id) {
        RuleDefinitionEntity e = service.findById(id);
        if (e == null) {
            throw new com.skyfalling.mousika.ui.web.common.BusinessException(404, "rule not found: " + id);
        }
        return ApiResponse.ok(e);
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> list(@RequestParam(required = false) Integer status,
                                                 @RequestParam(required = false) Integer useType,
                                                 @RequestParam(required = false) String ruleKind,
                                                 @RequestParam(required = false) String keyword,
                                                 @RequestParam(defaultValue = "1") int pageNumber,
                                                 @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(service.page(status, useType, ruleKind, keyword, pageNumber, pageSize));
    }
}
