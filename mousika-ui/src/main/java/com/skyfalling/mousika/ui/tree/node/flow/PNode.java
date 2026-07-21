package com.skyfalling.mousika.ui.tree.node.flow;


import com.skyfalling.mousika.ui.tree.node.define.BranchNode;
import com.skyfalling.mousika.ui.tree.node.define.FlowNode;
import lombok.Getter;

/**
 * Parallel并行节点
 * <pre>
 *      pN
 *     / | \
 *    /  |  \
 *   fN  fN fN
 * </pre>
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 * Created on 2022-07-19
 */
@Getter
public class PNode extends BranchNode<FlowNode> {

    public PNode() {
        super("=");
    }
}
