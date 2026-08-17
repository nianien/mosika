package com.nianien.mosika.ui.tree.node.define;

import lombok.Data;

/**
 * 可参与可视化执行编排的节点基类
 * <p>
 * 该递归域只描述执行拓扑，通过 {@link FlowNode} 和 {@link BranchNode} 区分原子节点与结构节点
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
@Data
public abstract class UINode extends NameNode implements TypeNode {
}
