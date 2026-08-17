package com.nianien.mosika.ui.web.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * {@code udf_definition} 表的实体映射
 * <p>
 * {@code group} 与 {@code name} 组成规则表达式中使用的完整函数路径，{@code source}
 * 在进入运行态前会由 Core 的 JavaScript 运行时完成编译、可执行性和函数名称校验
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UdfDefinitionEntity {

    /** 数据库自增主键 */
    private Long id;

    /** 命名空间数据库主键 */
    @JsonIgnore
    private Long namespaceId;

    /** 命名空间业务编码 */
    private String namespace;

    /** 点分隔的命名空间，如 content.generation；空字符串表示顶层 */
    private String group;

    /** 命名空间内的 JavaScript 函数名称 */
    private String name;

    /** 面向用户展示的 UDF 业务描述 */
    private String description;

    /** 求值结果必须是可执行函数的 JavaScript 源码 */
    private String source;

    /** 1 启用；0 停用 */
    private Integer status;

    /** 乐观锁版本号，每次正文或状态写入成功后递增 */
    private Long version;

    /** SQLite 记录创建时间字符串 */
    private String createdAt;

    /** SQLite 记录最后更新时间字符串 */
    private String updatedAt;
}
