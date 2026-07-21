package com.skyfalling.mousika.ui.tree.node.flow;


import com.skyfalling.mousika.ui.tree.node.rule.RNode;
import lombok.Getter;
import lombok.Setter;

/**
 * Judge复杂条件节点
 * <pre>
 *     jN
 *    / |
 *   /  |
 *  rN  fN
 * </pre>
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 * Created on 2022-07-19
 */
@Getter
@Setter
public class JNode extends CNode {

    /**
     * 默认为true,避免NPE
     */
    private RNode rule = new RNode("true");

    public JNode() {
        super("?");
    }


    @Override
    public String ruleExpr() {
        return (isNegative() ? "!" : "") + rule.ruleExpr();
    }
}
