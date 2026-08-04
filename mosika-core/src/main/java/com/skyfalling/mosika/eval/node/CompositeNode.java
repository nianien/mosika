package com.skyfalling.mosika.eval.node;


import com.skyfalling.mosika.eval.context.RuleContext;
import com.skyfalling.mosika.eval.result.EvalResult;

/**
 * 复合节点
 * Created on 2023/3/30
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
public class CompositeNode extends ExprNode {

    private final RuleNode ruleNode;

    public CompositeNode(String expression, RuleNode ruleNode) {
        super(expression);
        this.ruleNode = ruleNode;
    }

    @Override
    public EvalResult eval(RuleContext context) {
        EvalResult result = context.visit(ruleNode);
        EvalResult evalResult = new EvalResult(this.toString(), result.getResult(), result.isMatched());
        return evalResult;
    }


    @Override
    public String toString() {
        return this.expr() + "[" + ruleNode.expr() + "]";
    }
}
