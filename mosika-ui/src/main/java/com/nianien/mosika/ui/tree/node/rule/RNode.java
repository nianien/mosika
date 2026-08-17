package com.nianien.mosika.ui.tree.node.rule;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonCreator.Mode;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.nianien.mosika.ui.tree.node.define.RuleNode;
import lombok.Data;

/**
 * 保存规则引用和调用参数的原子规则节点
 * <p>
 * 未设置 {@code args} 时直接生成 {@code expr}
 * 设置 {@code args} 时生成带三引号参数模板的规则调用
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 * Created on 2022-07-19
 */
@Data
public class RNode extends RuleNode {

    /** 规则表达式或规则定义标识 */
    private String expr;

    /** 规则调用参数 */
    private String args;

    /**
     * 创建原子规则节点
     *
     * @param expr 规则表达式或规则定义ID
     */
    @JsonCreator(mode = Mode.PROPERTIES)
    public RNode(@JsonProperty("expr") String expr) {
        this.expr = expr;
    }


    /**
     * 生成原子规则 DSL
     *
     * @return 原子规则 DSL 表达式
     */
    @Override
    public String ruleExpr() {
        if (args == null || args.isBlank()) {
            return expr;
        }
        return expr + "(\"\"\"" + args.trim() + "\"\"\")";
    }
}
