package com.skyfalling.mosika.eval.result;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * 单个执行节点的递归详情
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
@Getter
public class RuleResult extends EvalResult {
    /**
     * 根据节点评估结果创建规则详情
     *
     * @param result 节点评估结果
     * @param desc   规则描述
     */
    public RuleResult(EvalResult result, String desc) {
        super(result.getExpr(), result.getResult(), result.isMatched());
        this.desc = desc;
    }

    /**
     * 规则描述
     */
    private String desc;

    /**
     * 按执行结构保存的子规则详情
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
