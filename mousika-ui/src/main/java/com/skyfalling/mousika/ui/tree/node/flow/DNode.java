package com.skyfalling.mousika.ui.tree.node.flow;

import com.skyfalling.mousika.ui.tree.node.define.BranchNode;
import com.skyfalling.mousika.ui.tree.node.define.FlowNode;
import lombok.Getter;
import lombok.Setter;

/**
 * Decision决策节点
 * <pre>
 *      dN
 *     / | \
 *    /  |  \
 *   cN  cN  fN
 * </pre>
 * action表示所有条件均未命中时执行的默认分支，可以为空。
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 * Created on 2022-07-19
 */
@Getter
@Setter
public class DNode extends BranchNode<CNode> {

    /**
     * 默认分支。
     */
    private FlowNode action;

    public DNode() {
        super("-");
    }
}
