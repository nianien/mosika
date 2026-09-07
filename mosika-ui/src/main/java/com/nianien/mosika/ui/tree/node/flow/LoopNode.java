package com.nianien.mosika.ui.tree.node.flow;

import com.nianien.mosika.ui.tree.node.define.FlowNode;
import com.nianien.mosika.ui.tree.node.rule.BooleanNode;
import lombok.Data;

/**
 * 有界循环结构节点
 * <p>
 * 每轮先判断 {@code rule}，命中后执行 {@code next}
 * 条件未命中或达到 {@code max} 时结束
 */
@Data
public class LoopNode extends FlowNode<BooleanNode> {

    /** 最大执行轮数 */
    private int max;
}
