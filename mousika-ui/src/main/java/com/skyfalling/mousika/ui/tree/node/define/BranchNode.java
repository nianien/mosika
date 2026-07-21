package com.skyfalling.mousika.ui.tree.node.define;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * Branch分支节点，至少两个分支
 * <pre>
 *      bN
 *     / | \
 *    /  |  \
 *   fN  fN fN
 * </pre>
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 * Created on 2022-07-19
 */
@Getter
public abstract class BranchNode<T extends UINode> extends FlowNode implements TypeNode {

    private List<T> branches = new ArrayList<>();

    public BranchNode(String expr) {
        super(expr);
    }

    /**
     * 添加分支节点
     *
     * @param node
     * @return
     */
    public BranchNode<T> addBranch(T node) {
        this.branches.add(node);
        return this;
    }
}
