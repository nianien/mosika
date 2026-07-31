package com.skyfalling.mousika.ui.web.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * {@code rule_flow} 表的实体映射。
 * <p>
 * {@code id} 即 {@code flowId}；{@code ruleTree} 是 UI AST 的 JSON 表示（
 * 编辑与持久化的唯一事实来源）；装配 {@link com.skyfalling.mousika.suite.RuleSuite}
 * 时先反序列化为 {@link com.skyfalling.mousika.ui.tree.node.TreeNode} 再
 * {@code toRule().expr()} 得到 DSL，构造 {@link com.skyfalling.mousika.suite.RuleFlowDefinition}。
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleFlowEntity {

    private Long id;
    private String name;
    private String description;
    /** UI AST JSON（TreeNode.toJson 的产物） */
    private String ruleTree;
    /** 1 启用；0 停用 */
    private Integer status;
    private Long version;
    private String createdAt;
    private String updatedAt;
}
