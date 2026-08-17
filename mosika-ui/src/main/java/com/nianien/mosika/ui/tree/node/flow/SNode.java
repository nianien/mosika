package com.nianien.mosika.ui.tree.node.flow;


import com.nianien.mosika.ui.tree.node.define.BranchNode;
import com.nianien.mosika.ui.tree.node.define.UINode;
import lombok.Getter;

/**
 * 串行组合结构节点
 * <p>
 * {@code branches} 中的每个元素都是完整的执行子树，并严格按照列表顺序执行
 * 该节点表达组合子树之间的显式顺序，不替代 {@code ANode.next} 表达的普通动作链
 * <pre>
 *       sN
 *      / | \
 *     /  |  \
 *   uN  uN  uN
 * </pre>
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 * Created on 2022-07-19
 */
@Getter
public class SNode extends BranchNode<UINode> {
}
