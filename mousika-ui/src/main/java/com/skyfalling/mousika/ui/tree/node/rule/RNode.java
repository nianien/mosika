package com.skyfalling.mousika.ui.tree.node.rule;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonCreator.Mode;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.skyfalling.mousika.ui.tree.node.define.IRNode;
import com.skyfalling.mousika.ui.tree.node.define.UINode;
import lombok.Getter;
import lombok.Setter;

/**
 * 纯规则递归域中的原子规则节点。
 * <p>
 * 继承的{@code expr}保存稳定的规则表达式或规则定义ID，节点只负责产生匹配结果，
 * 不持有或执行流程动作。{@link LNode}和{@link HNode}通过继承本类参与同一规则递归域。
 * <p>
 * 继承的{@code label}是所有UI节点共有的通用展示标签，表示节点类别；
 * {@code name}是规则节点自身可编辑的规则名称或别名。两者均不参与规则求值，
 * 但只有{@code name}用于规则在业务UI中的命名。
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 * Created on 2022-07-19
 */

public class RNode extends UINode implements IRNode {

    /**
     * 规则名称或别名，仅用于业务UI展示，不参与规则表达式求值。
     * <p>
     * 逻辑与、逻辑或和命中数组合节点均继承该字段；未设置时由调用方根据
     * 规则定义或节点类型提供回退文案。该字段不同于{@code UINode.label}。
     */
    @Getter
    @Setter
    private String name;

    /**
     * 是否对当前原子规则整体取反。
     */
    @Getter
    @Setter
    private boolean negative;

    /**
     * 创建未取反的原子规则节点。
     *
     * @param expr 规则表达式或规则定义ID
     */
    @JsonCreator(mode = Mode.PROPERTIES)
    public RNode(@JsonProperty("expr") String expr) {
        super(expr);
    }

    /**
     * 创建原子规则节点并指定取反状态。
     *
     * @param expr     规则表达式或规则定义ID
     * @param negative {@code true}表示取反
     */
    public RNode(String expr, boolean negative) {
        super(expr);
        this.negative = negative;
    }

    /**
     * 生成原子规则DSL。
     *
     * @return 原子规则DSL表达式
     */
    @Override
    public String ruleExpr() {
        return (negative ? "!" : "") + getExpr();
    }
}
