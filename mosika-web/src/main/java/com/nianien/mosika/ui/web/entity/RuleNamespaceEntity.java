package com.nianien.mosika.ui.web.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * {@code rule_namespace} 表的实体映射
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleNamespaceEntity {

    /** 数据库自增主键 */
    @JsonIgnore
    private Long id;

    /** 命名空间业务编码 */
    private String code;

    /** 面向用户展示的名称 */
    private String name;

    /** 命名空间说明 */
    private String description;

    /** 1 启用；0 停用 */
    private Integer status;

    /** SQLite 记录创建时间字符串 */
    private String createdAt;

    /** SQLite 记录最后更新时间字符串 */
    private String updatedAt;
}
