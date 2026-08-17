package com.nianien.mosika.eval.node;

import com.nianien.mosika.eval.context.RuleContext;
import com.nianien.mosika.eval.result.EvalResult;
import lombok.Getter;

/**
 * 条件执行节点
 * Created on 2023/3/28
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
@Getter
public class CaseNode implements RuleNode {
    /**
     * 条件节点
     */
    private final RuleNode condition;
    /**
     * 为真节点
     */
    private final RuleNode trueCase;

    /**
     * 为假节点
     */
    private final RuleNode falseCase;


    /**
     * @param condition 条件节点
     * @param trueCase  为真节点
     * @param falseCase 为假节点
     */
    public CaseNode(RuleNode condition, RuleNode trueCase, RuleNode falseCase) {
        this.condition = condition;
        this.trueCase = trueCase;
        this.falseCase = falseCase;
    }

    /**
     * @param condition 条件节点
     * @param trueCase  为真节点
     */
    public CaseNode(RuleNode condition, RuleNode trueCase) {
        this(condition, trueCase, null);
    }


    @Override
    public EvalResult eval(RuleContext context) {
        EvalResult result = null;
        boolean succeed = context.visit(condition).isMatched();
        if (succeed) {
            if (trueCase != null) {
                result = context.visit(trueCase);
            }
        } else {
            if (falseCase != null) {
                result = context.visit(falseCase);
            }
        }
        return result != null ? new EvalResult(expr(), result.getResult(), result.isMatched()) : new EvalResult(expr(), null);
    }

    @Override
    public String expr() {
        if (trueCase != null && falseCase != null) {
            return condition + "?" + trueCase + ":" + falseCase;
        } else if (trueCase != null) {
            return condition + "?" + trueCase;
        } else if (falseCase != null) {
            return condition.not() + "?" + falseCase;
        }
        return condition.expr();
    }

    @Override
    public String toString() {
        return "(" + this.expr() + ")";
    }
}
