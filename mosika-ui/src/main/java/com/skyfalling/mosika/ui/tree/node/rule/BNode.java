package com.skyfalling.mosika.ui.tree.node.rule;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonCreator.Mode;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 可整体取反的布尔规则节点
 * <p>
 * 该节点在 {@link RNode} 的规则调用能力上增加 {@code negative}
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 * Created on 2022-07-19
 */
@Data
public class BNode extends RNode {

    /** 是否对整个规则表达式取反 */
    private boolean negative;

    /**
     * 创建布尔规则节点
     *
     * @param expr 规则表达式或规则定义ID
     */
    @JsonCreator(mode = Mode.PROPERTIES)
    public BNode(@JsonProperty("expr") String expr) {
        super(expr);
    }

    /**
     * 生成包含整体取反语义的规则 DSL
     *
     * @return 布尔规则 DSL 表达式
     */
    @Override
    public String ruleExpr() {
        return (negative ? "!" : "") + super.ruleExpr();
    }

}
