package com.skyfalling.mousika.ui.tree.node.define;

/**
 * 可生成纯规则表达式的节点契约。
 * <p>
 * 实现类负责表达规则匹配及可选取反，不在{@link #ruleExpr()}中执行流程动作。
 * {@code CNode}/{@code JNode}虽然属于流程递归域，也通过该接口暴露其条件侧的规则表达式。
 * Created on 2023/5/2
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
public interface IRNode {

    /**
     * 设置是否对当前规则整体取反。
     *
     * @param negative {@code true}表示取反
     */
    void setNegative(boolean negative);

    /**
     * 判断当前规则是否取反。
     *
     * @return {@code true}表示取反
     */
    boolean isNegative();

    /**
     * 生成当前节点及其规则子树对应的DSL表达式。
     *
     * @return 规则DSL表达式
     */
    String ruleExpr();
}
