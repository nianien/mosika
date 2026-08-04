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
     */
    EvalResult eval(String ruleId);

    /**
     * 执行规则节点
     */
    EvalResult visit(RuleNode node);

    /**
     * 获取执行结果
     */
    List<RuleResult> getRuleResults();


    /**
     * 获取当前评估节点
     *
     * @return
     */
    EvalNode getCurrentEval();

    /**
     * 设置当前评估节点
     *
     * @param node
     */
    void setCurrentEval(EvalNode node);


}