package com.skyfalling.mousika.ui.tree.node.flow;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonCreator.Mode;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.skyfalling.mousika.ui.tree.node.define.FlowNode;
import lombok.Getter;
import lombok.Setter;

/**
 * 原子动作流程节点。
 * <p>
 * 继承的{@code expr}保存动作表达式，{@code next}保存零个或一个任意流程后继。
 * 动作完成后才进入后继节点；后继关系属于流程递归，不由画布位置推断。
 * <pre>
 *     aN
 *      |
 *      |
 *     fN
 * </pre>
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 * Created on 2022-07-19
 */
public class ANode extends FlowNode {

    /**
     * 动作完成后的可选流程后继。
     */
    @Getter
    @Setter
    private FlowNode next;

    /**
     * 创建动作节点。
     *
     * @param expr 动作表达式
     */
    @JsonCreator(mode = Mode.PROPERTIES)
    public ANode(@JsonProperty("expr") String expr) {
        super(expr);
    }
}
