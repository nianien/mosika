package com.skyfalling.mousika.eval.result;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * 规则执行结果
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
@Getter
public class RuleResult extends EvalResult {
    /**
     * @param result
     * @param desc
     * @author skyfalling {@literal <skyfalling@live.com>}
     */
    public RuleResult(EvalResult result, String desc) {
        super(result.getExpr(), result.getResult(), result.isMatched());
        this.desc = desc;
    }

    /**
     * 规则描述
     *
     * @author skyfalling {@literal <skyfalling@live.com>}
     */
    private String desc;
    /**
     * 子规则
     *
     * @author skyfalling {@literal <skyfalling@live.com>}
     */

    private List<RuleResult> subRules = new ArrayList<>();


    @Override
    public String toString() {
        return "RuleResult("
                + "expr=" + expr
                + ",result=" + result
                + (desc == null || desc.isEmpty() ? "" : ",desc='" + desc + '\'')
                + (subRules.isEmpty() ? "" : ",subRules=" + subRules)
                + ')';
    }
}
