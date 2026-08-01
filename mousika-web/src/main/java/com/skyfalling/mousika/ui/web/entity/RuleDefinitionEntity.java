package com.skyfalling.mousika.ui.web.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * {@code rule_definition} 表的实体映射。
 * <p>
 * 每条记录对应内核 {@link com.skyfalling.mousika.engine.RuleDefinition} 中的一条原子叶子规则：
 * {@code id} 即 {@code ruleId}；{@code name} 是 UI 侧规则名称；{@code description}
 * 对应 {@code RuleDefinition.desc}；{@code expression} 是可执行 DSL（决策表时存整段 JSON）。
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleDefinitionEntity {

    private Long id;
    private String name;
    private String description;
    private String expression;
    /** 0 原子；1 决策表；2 复合规则（历史兼容，UI 不再产生） */
    private Integer useType;
    /** 规则分类：condition 条件规则（用于判断/条件节点）/ action 动作规则（用于动作节点）。UI 元数据，不参与内核求值。 */
    private String ruleKind;
    /** 1 启用；0 停用（逻辑删除） */
    private Integer status;
    private Long version;
    private String createdAt;
    private String updatedAt;
}
