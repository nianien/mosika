package com.skyfalling.mosika.ui.tree.node.flow;

import com.skyfalling.mosika.ui.tree.node.define.FlowNode;
import com.skyfalling.mosika.ui.tree.node.rule.RNode;

/**
 * 动作原子节点
 * <p>
 * 执行绑定的 {@code rule} 后无条件进入 {@code next}
 * 连续动作直接通过 {@code next} 形成自然动作链
 * <pre>
 *     aN
 *      |
 *     uN
 * </pre>
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 * Created on 2022-07-19
 */
public class ANode extends FlowNode<RNode> {
}
