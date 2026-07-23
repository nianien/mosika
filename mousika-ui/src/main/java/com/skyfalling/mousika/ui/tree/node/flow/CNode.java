package com.skyfalling.mousika.ui.tree.node.flow;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonCreator.Mode;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.skyfalling.mousika.ui.tree.node.define.FlowNode;
import com.skyfalling.mousika.ui.tree.node.define.IRNode;
import lombok.Getter;
import lombok.Setter;

/**
 * 原子条件流程节点。
 * <p>
 * 继承的{@code expr}保存条件表达式；条件命中时执行可选的{@code action}流程。
 * {@code action}为空时仅产生条件匹配结果。该节点属于流程递归域，不是可嵌入
 * {@code LNode}/{@code HNode}的纯规则子树；新建规则的规范表示由{@link JNode}承载，
 * 本类型继续用于兼容已有UI树。
 * <pre>
 *     cN
 *      |
 *      |
 *     fN
 * </pre>
 * @author skyfalling {@literal <skyfalling@live.com>}
 * Created on 2022-07-19
 */
@Getter
@Setter
public class CNode extends FlowNode implements IRNode {

    /**
     * 是否对原子条件表达式取反。
     */
    private boolean negative;

    /**
     * 条件命中后执行的节点；为空时当前节点仅表示条件本身。
     */
    private FlowNode action;

    /**
     * 创建原子条件节点。
     *
     * @param expr 条件表达式
     */
    @JsonCreator(mode = Mode.PROPERTIES)
    public CNode(@JsonProperty("expr") String expr) {
        super(expr);
    }

    /**
     * 生成原子条件DSL，不包含命中后的{@code action}流程。
     *
     * @return 条件DSL表达式
     */
    @Override
    public String ruleExpr() {
        return (negative ? "!" : "") + getExpr();
    }
}
