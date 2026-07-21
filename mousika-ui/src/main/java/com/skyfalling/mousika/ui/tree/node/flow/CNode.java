package com.skyfalling.mousika.ui.tree.node.flow;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonCreator.Mode;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.skyfalling.mousika.ui.tree.node.define.FlowNode;
import com.skyfalling.mousika.ui.tree.node.define.IRNode;
import lombok.Getter;
import lombok.Setter;

/**
 * Condition条件节点
 * <pre>
 *     cN
 *      |
 *      |
 *     fN
 * </pre>
 * action为空时表示纯条件规则，非空时表示条件执行。
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 * Created on 2022-07-19
 */
@Getter
@Setter
public class CNode extends FlowNode implements IRNode {

    /**
     * 是否取反
     */
    private boolean negative;

    /**
     * 条件命中后执行的节点；为空时当前节点仅表示条件本身。
     */
    private FlowNode action;

    @JsonCreator(mode = Mode.PROPERTIES)
    public CNode(@JsonProperty("expr") String expr) {
        super(expr);
    }

    @Override
    public String ruleExpr() {
        return (negative ? "!" : "") + getExpr();
    }
}
