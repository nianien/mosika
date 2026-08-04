package com.skyfalling.mosika.ui.tree.node.flow;


import com.skyfalling.mosika.ui.tree.node.define.BranchNode;
import com.skyfalling.mosika.ui.tree.node.define.FlowNode;
import lombok.Getter;

/**
 * 串行流程结构节点。
 * <p>
 * {@code branches}中的每个元素都是可替换的完整流程子树，并严格按照列表顺序执行。
 * 显式串行结构保存子树作用域，不能用节点纵向位置或隐含连线替代。
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

    /**
     * 创建串行节点，使用{@code S}作为稳定结构标识。
     */
    public SNode() {
        super("S");
    }
}
