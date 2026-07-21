package com.skyfalling.mousika.ui.tree.node.flow;


import com.skyfalling.mousika.ui.tree.node.define.BranchNode;
import com.skyfalling.mousika.ui.tree.node.define.FlowNode;
import lombok.Getter;

/**
 * Serial串行节点
 * <pre>
 *      sN
 *     / | \
 *    /  |  \
 *   fN  fN fN
 * </pre>
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 * Created on 2022-07-19
 */
@Getter
public class SNode extends BranchNode<FlowNode> {

    public SNode() {
        super("+");
    }
}
