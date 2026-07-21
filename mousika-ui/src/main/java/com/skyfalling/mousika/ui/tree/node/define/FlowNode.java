package com.skyfalling.mousika.ui.tree.node.define;

/**
 * Flow流式节点
 * <pre>
 *     fN
 *      |
 *     fN
 * </pre>
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 * Created on 2022-07-19
 */
public abstract class FlowNode extends UINode implements TypeNode {

    public FlowNode(String expr) {
        super(expr);
    }
}