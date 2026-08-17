package com.nianien.mosika.ui.tree.node.flow;

import com.nianien.mosika.ui.tree.node.define.FlowNode;
import com.nianien.mosika.ui.tree.node.rule.BNode;

/**
 * 条件原子节点
 * <p>
 * {@code next} 只表示条件命中分支，未命中时结束当前条件分支
 * <pre>
 *     cN
 *      |
 *     uN
 * </pre>
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 * Created on 2022-07-19
 */
public class CNode extends FlowNode<BNode> {
}
