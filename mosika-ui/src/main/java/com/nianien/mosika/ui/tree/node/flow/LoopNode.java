package com.nianien.mosika.ui.tree.node.flow;

import com.nianien.mosika.ui.tree.node.define.UINode;
import com.nianien.mosika.ui.tree.node.rule.BooleanNode;
import lombok.Data;

/**
 * 有界循环结构节点
 * <p>
 * 每轮先判断 {@code condition}，命中后执行 {@code action}
 * 条件未命中或达到 {@code max} 时结束
 */
@Data
public class LoopNode extends UINode {

    /** 最大执行轮数 */
    private int max;

    /** 每轮执行前判断的条件规则 */
    private BooleanNode condition;

    /** 条件命中后执行的完整流程子树 */
    private UINode action;
}
