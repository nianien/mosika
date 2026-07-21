package com.skyfalling.mousika.ui.tree.node.define;

/**
 * 规则节点接口定义
 * Created on 2023/5/2
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
public interface IRNode {

    /**
     * 设置是否取反
     *
     * @param negative
     */
    void setNegative(boolean negative);

    /**
     * 判断是否取反
     *
     * @return
     */
    boolean isNegative();

    /**
     * 规则表达式
     *
     * @return
     */
    String ruleExpr();
}
