package com.skyfalling.mousika.ui.tree.node.define;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * 包含有序子树列表的流程结构节点。
 * <p>
 * 该基类只负责保存分支顺序，不统一规定最小分支数量或执行方式。
 * {@code SNode}/{@code PNode}包含流程子树，{@code DNode}包含条件分支；
 * 各具体节点负责解释这些分支并校验自身约束。
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

    /**
     * 按执行或判断顺序保存的直接子节点。
     */
    private List<T> branches = new ArrayList<>();

    /**
     * 创建分支结构节点。
     *
     * @param expr 结构类型标识
     */
    public BranchNode(String expr) {
        super(expr);
    }

    /**
     * 将子节点追加到有序分支列表末尾。
     *
     * @param node 待追加的直接子节点
     * @return 当前分支结构节点
     */
    public BranchNode<T> addBranch(T node) {
        this.branches.add(node);
        return this;
    }
}
