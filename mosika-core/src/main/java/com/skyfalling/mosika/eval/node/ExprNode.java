package com.skyfalling.mosika.eval.node;

import com.skyfalling.mosika.eval.context.RuleContext;
import com.skyfalling.mosika.eval.result.EvalResult;
import lombok.Getter;

/**
 * 由规则上下文按规则 ID 求值的普通叶子节点
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
@Getter
public class ExprNode implements RuleNode {

    /**
     * 规则 ID
     */
    private final String expression;

    /**
     * 创建普通叶子节点
     *
     * @param expression 规则 ID
     */
    public ExprNode(String expression) {
        this.expression = expression;
    }

    @Override
    public EvalResult eval(RuleContext context) {
        return context.eval(expression);
    }

    @Override
    public String expr() {
        return expression;
    }

    @Override
    public String toString() {
        return expression;
    }
}
