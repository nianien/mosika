package com.skyfalling.mosika.ui.tree.node.define;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * 包含有序子树列表的组合结构节点
 * <p>
 * 结构节点不绑定规则也不拥有 {@code next}
 * 该基类只保存分支顺序，具体节点负责解释分支的执行方式并校验自身约束
 * <pre>
 *       bN
 *      / | \
 *     /  |  \
 *   uN  uN  uN
 * </pre>
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 * Created on 2022-07-19
 */

public abstract class BranchNode<T extends UINode> extends UINode implements TypeNode {

    /** 按执行或判断顺序保存的直接子节点 */
    @Getter
    private List<T> branches = new ArrayList<>();


    /**
     * 将子节点追加到有序分支列表末尾
     *
     * @param node 待追加的直接子节点
     * @return 当前分支结构节点
     */
    public BranchNode<T> addBranch(T node) {
        this.branches.add(node);
        return this;
    }
}
