package com.nianien.mosika.eval.context;

import com.nianien.mosika.eval.EvalNode;
import com.nianien.mosika.eval.node.ExprNode;
import com.nianien.mosika.eval.node.RuleNode;
import com.nianien.mosika.eval.result.EvalResult;
import com.nianien.mosika.eval.result.RuleResult;

import java.util.List;

/**
 * 规则执行上下文
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
public interface RuleContext extends UdfContext {

    /**
     * 评估已经解析调用参数的叶子节点
     *
     * @param node 叶子节点
     * @return 叶子规则评估结果
     */
    EvalResult eval(ExprNode node);

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
