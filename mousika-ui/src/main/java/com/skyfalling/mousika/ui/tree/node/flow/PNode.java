package com.skyfalling.mousika.ui.tree.node.flow;


import com.skyfalling.mousika.ui.tree.node.define.BranchNode;
import com.skyfalling.mousika.ui.tree.node.define.FlowNode;
import lombok.Getter;

/**
 * 并行流程结构节点。
 * <p>
 * {@code branches}中的每个元素都是可替换的完整流程子树。各分支并发执行，
 * 当前结构等待所有分支完成；列表顺序只用于稳定遍历和序列化，不改变并发语义。
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

    /**
     * 创建并行节点，使用{@code P}作为稳定结构标识。
     */
    public PNode() {
        super("P");
    }
}
