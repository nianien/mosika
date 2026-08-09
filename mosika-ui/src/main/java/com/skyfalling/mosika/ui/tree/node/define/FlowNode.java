package com.skyfalling.mosika.ui.tree.node.define;

import lombok.Data;

/**
 * 绑定一棵规则表达式并拥有单一出口的原子执行节点
 * <p>
 * {@link com.skyfalling.mosika.ui.tree.node.flow.ANode} 将出口解释为无条件后继
 * {@link com.skyfalling.mosika.ui.tree.node.flow.CNode} 将出口解释为条件命中分支
 * <pre>
 *     fN
 *      |
 *     uN
 * </pre>
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 * Created on 2022-07-19
 */
@Data
public abstract class FlowNode<T extends RuleNode> extends UINode implements TypeNode {

    /** 当前原子节点绑定的规则 */
    private T rule;

    /** 单一出口，具体语义由原子节点类型决定 */
    private UINode next;
}
