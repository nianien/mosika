package com.skyfalling.mosika.eval.context;

import com.skyfalling.mosika.eval.EvalNode;
import com.skyfalling.mosika.eval.node.RuleNode;
import com.skyfalling.mosika.eval.result.EvalResult;
import com.skyfalling.mosika.eval.result.RuleResult;

import java.util.List;

/**
 * 规则执行上下文
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
public interface RuleContext extends UdfContext {

    /**
     * 评估叶子规则
     *
     * @param ruleId 规则 ID
     * @return 叶子规则评估结果
     */
    EvalResult eval(String ruleId);

    /**
     * 执行规则节点
     *
     * @param node 规则节点
     * @return 节点评估结果
     */
    EvalResult visit(RuleNode node);

    /**
     * 获取本次执行的递归规则详情
     *
     * @return 规则详情
     */
    List<RuleResult> getRuleResults();


    /**
     * 获取当前评估节点
     *
     * @return 当前评估节点
     */
    EvalNode getCurrentEval();

    /**
     * 设置当前评估节点
     *
     * @param node 当前评估节点
     */
    void setCurrentEval(EvalNode node);


}
