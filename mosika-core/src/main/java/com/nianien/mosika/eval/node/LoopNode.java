package com.nianien.mosika.eval.node;

import com.nianien.mosika.eval.EvalCacheScope;
import com.nianien.mosika.eval.context.RuleContext;
import com.nianien.mosika.eval.result.EvalResult;
import lombok.Getter;

/**
 * 按条件重复执行动作的有界循环节点
 */
@Getter
public class LoopNode extends AbstractRuleNode {

    private final int max;
    private final RuleNode condition;
    private final RuleNode action;

    /**
     * 创建有界循环节点
     *
     * @param max 最大执行轮数
     * @param condition 每轮执行前判断的条件
     * @param action 条件命中后执行的动作
     */
    public LoopNode(int max, RuleNode condition, RuleNode action) {
        this.max = max;
        this.condition = condition;
        this.action = action;
    }

    /**
     * 每轮让条件和动作共享缓存并在下一轮重新计算
     *
     * @param context 规则上下文
     * @return 循环结构的评估结果
     */
    @Override
    public EvalResult eval(RuleContext context) {
        EvalCacheScope parentScope = context.getCurrentEval().getCacheScope();
        EvalCacheScope iterationScope = new EvalCacheScope();
        boolean evaluated = false;
        try {
            for (int i = 0; i < max; i++) {
                iterationScope.reset();
                evaluated = true;
                if (!context.visit(condition, iterationScope).isMatched()) {
                    break;
                }
                context.visit(action, iterationScope);
            }
            return new EvalResult(expr(), null, true);
        } finally {
            if (evaluated) {
                parentScope.invalidate();
            }
        }
    }

    @Override
    protected String computeExpr() {
        return "loop(" + max + "," + condition + "," + action + ")";
    }

    @Override
    public String toString() {
        return expr();
    }
}
