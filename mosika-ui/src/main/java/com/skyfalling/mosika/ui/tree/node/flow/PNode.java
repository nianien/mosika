package com.skyfalling.mosika.ui.tree.node.flow;


import com.skyfalling.mosika.ui.tree.node.define.BranchNode;
import com.skyfalling.mosika.ui.tree.node.define.UINode;
import lombok.Getter;

/**
 * 并行组合结构节点
 * <p>
 * {@code branches} 中的每个元素都是完整的执行子树
 * 所有分支并发执行，当前结构等待全部分支完成后结束
 * 列表顺序只用于稳定编辑、遍历和序列化，不表示执行先后
 * <pre>
 *       pN
 *      / | \
 *     /  |  \
 *   uN  uN  uN
 * </pre>
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 * Created on 2022-07-19
 */
@Getter
public class PNode extends BranchNode<UINode> {
}
