package com.nianien.mosika.eval.node;

import com.nianien.mosika.eval.context.RuleContext;
import com.nianien.mosika.eval.result.EvalResult;
import lombok.Getter;

/**
 * 条件取反
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
@Getter
public class NotNode extends AbstractRuleNode {

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

    @Override
    protected String computeExpr() {
        return "!" + node.toString();
    }

    @Override
    public String toString() {
        return this.expr();
    }
}
