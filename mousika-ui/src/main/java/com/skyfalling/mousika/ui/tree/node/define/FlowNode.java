package com.skyfalling.mousika.ui.tree.node.define;

/**
 * 流程递归域的抽象基类。
 * <p>
 * 所有可出现在主流程中的节点均继承该类。具体节点通过{@code next}、
 * {@code action}或{@code branches}显式维护递归边，不依赖画布位置或连线方向推断语义。
 * <pre>
 *     fN
 *      |
 *     fN
 * </pre>
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 * Created on 2022-07-19
 */
public abstract class FlowNode extends UINode implements TypeNode {

    /**
     * 创建流程节点。
     *
     * @param expr 动作、条件表达式或流程结构标识
     */
    public FlowNode(String expr) {
        super(expr);
    }
}
