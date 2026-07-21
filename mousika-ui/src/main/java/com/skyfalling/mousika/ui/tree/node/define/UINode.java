package com.skyfalling.mousika.ui.tree.node.define;

import lombok.Getter;

/**
 * UI节点
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
@Getter
public abstract class UINode implements TypeNode {

    /**
     * 节点表达式
     */
    private final String expr;

    public UINode(String expr) {
        this.expr = expr;
    }
}