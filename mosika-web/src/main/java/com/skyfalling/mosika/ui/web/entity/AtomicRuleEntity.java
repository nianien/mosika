package com.skyfalling.mosika.ui.web.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.skyfalling.mosika.ui.web.common.RuleIds;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * {@code atomic_rule} 表的实体映射
 * <p>
 * {@code id} 是数据库自增主键，进入 UI 树、REST API 和 Core 的 {@code ruleId}
 * 由 {@code r + id} 实时派生，不单独持久化
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AtomicRuleEntity {

    /** 数据库自增主键 */
    @JsonIgnore
    private Long id;

    /** 所属命名空间数据库主键 */
    @JsonIgnore
    private Long namespaceId;

    /** 所属命名空间业务编码 */
    private String namespace;

    /** 面向用户展示的规则名称 */
    private String name;

    /** 规则业务描述 */
    private String description;

    /** JavaScript 原子规则表达式 */
    private String expression;

    /** 规则分类：condition 条件规则 / action 动作规则 */
    private String kind;

    /** 1 启用；0 停用 */
    private Integer status;

    /** 乐观锁版本号 */
    private Long version;

    /** SQLite 记录创建时间字符串 */
    private String createdAt;

    /** SQLite 记录最后更新时间字符串 */
    private String updatedAt;

    /** 返回由数据库主键派生的原子规则 ID */
    @JsonProperty(value = "ruleId", access = JsonProperty.Access.READ_ONLY)
    public String getRuleId() {
        return RuleIds.ruleId(id);
    }
}
