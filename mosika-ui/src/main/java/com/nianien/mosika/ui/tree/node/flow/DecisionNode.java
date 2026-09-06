package com.nianien.mosika.ui.tree.node.flow;

import com.nianien.mosika.ui.tree.node.define.BranchNode;
import com.nianien.mosika.ui.tree.node.define.UINode;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

/**
 * 有序互斥决策结构节点
 * <p>
 * {@code branches} 按判断顺序保存 {@link ConditionNode}
 * 命中首个条件后执行该条件的 {@code next} 并停止判断后续分支
 * 所有条件均未命中时执行 {@code defaultBranch}
 * 默认分支可以是 {@code ActionNode/DecisionNode/LoopNode/ParallelNode/SerialNode}，不能是 {@code ConditionNode}
 * <pre>
 *       dN
 *      / | \
 *     /  |  \
 *   cN  cN  uN
 *        default
 * </pre>
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 * Created on 2022-07-19
 */
@Data
public class DecisionNode extends BranchNode<ConditionNode> {

    /** 所有条件均未命中时执行的默认分支 */
    private UINode defaultBranch;
}
