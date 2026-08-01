package com.skyfalling.mousika.eval.node;

import com.skyfalling.mousika.eval.context.RuleContext;
import com.skyfalling.mousika.eval.result.EvalResult;
import lombok.Getter;

/**
 * 条件取反
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
@Getter
public class NotNode implements RuleNode {

    private final RuleNode node;

    /**
     * 对node取反
     */
    public NotNode(RuleNode node) {
        this.node = node;
    }


    @Override
    public RuleNode not() {
        return node;
    }


    @Override
    public EvalResult eval(RuleContext context) {
        EvalResult result = context.visit(node);
        return new EvalResult(expr(), !result.isMatched());
    }

    public String expr() {
        return "!" + node.toString();
    }

    @Override
    public String toString() {
        return this.expr();
    }
}
