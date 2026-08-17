package com.nianien.mosika.ui.web.controller;

import com.nianien.mosika.ui.web.common.ApiResponse;
import com.nianien.mosika.ui.web.common.BusinessException;
import com.nianien.mosika.ui.web.entity.UdfDefinitionEntity;
import com.nianien.mosika.ui.web.service.UdfDefinitionService;
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

import java.util.Map;

/**
 * 用户可管理的 JavaScript UDF REST 接口
 * <p>
 * 提供 UDF 创建、乐观锁更新、逻辑停用、重新启用和分页查询能力
 * UDF 路径与源码校验由 {@link UdfDefinitionService} 统一完成
 */
@RestController
@RequestMapping("/api/udfs")
@RequiredArgsConstructor
public class UdfDefinitionController {

    /** UDF 定义业务服务 */
    private final UdfDefinitionService service;

    /**
     * 创建并默认启用 JavaScript UDF
     *
     * @param request UDF 定义请求
     * @return 已落库的 UDF 定义
     */
    @PostMapping
    public ApiResponse<UdfDefinitionEntity> create(@RequestBody UdfDefinitionEntity request) {
        return ApiResponse.ok(service.create(request));
    }

    /**
     * 按乐观锁更新 UDF 路径、描述和源码
     *
     * @param id      UDF 数据库 ID
     * @param request 包含期望版本号的新 UDF 定义
     * @return 更新后的 UDF 定义
     */
    @PutMapping("/{id}")
    public ApiResponse<UdfDefinitionEntity> update(@PathVariable long id,
                                                   @RequestBody UdfDefinitionEntity request) {
        return ApiResponse.ok(service.update(id, request));
    }

    /**
     * 按乐观锁逻辑停用 UDF
     *
     * @param id      UDF 数据库 ID
     * @param version 客户端持有的期望版本号
     * @return 空成功响应
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> disable(@PathVariable long id, @RequestParam long version) {
        service.disable(id, version);
        return ApiResponse.ok();
    }

    /**
     * 在完整套件编译校验通过后重新启用 UDF
     *
     * @param id      UDF 数据库 ID
     * @param version 客户端持有的期望版本号
     * @return 启用后的 UDF 定义
     */
    @PostMapping("/{id}/enable")
    public ApiResponse<UdfDefinitionEntity> enable(@PathVariable long id, @RequestParam long version) {
        return ApiResponse.ok(service.enable(id, version));
    }

    /**
     * 按 ID 查询 UDF 定义
     *
     * @param id UDF 数据库 ID
     * @return 对应 UDF 定义
     */
    @GetMapping("/{id}")
    public ApiResponse<UdfDefinitionEntity> get(@PathVariable long id) {
        UdfDefinitionEntity entity = service.findById(id);
        if (entity == null) {
            throw new BusinessException(404, "udf not found: " + id);
        }
        return ApiResponse.ok(entity);
    }

    /**
     * 按状态和关键字分页查询 UDF 定义
     *
     * @param status     启停状态，传 {@code null} 表示不过滤
     * @param namespace  规则命名空间，传空值表示默认命名空间
     * @param keyword    模糊查询关键字，传空值表示不过滤
     * @param pageNumber 从 1 开始的页码
     * @param pageSize   每页条数
     * @return 分页 UDF 定义
     */
    @GetMapping
    public ApiResponse<Map<String, Object>> list(@RequestParam(required = false) Integer status,
                                                 @RequestParam(required = false) String namespace,
                                                 @RequestParam(required = false) String keyword,
                                                 @RequestParam(defaultValue = "1") int pageNumber,
                                                 @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(service.page(status, namespace, keyword, pageNumber, pageSize));
    }
}
