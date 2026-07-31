package com.skyfalling.mousika.udf;

import com.skyfalling.mousika.annotation.Udf;
import com.skyfalling.mousika.eval.result.NodeResult;
import com.skyfalling.mousika.suite.RuleSuite;
import com.skyfalling.mousika.udf.Functions.Function2;
import com.skyfalling.mousika.udf.Functions.Function3;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * 规则流评估函数，使用方式如下：<br>
 * <p>
 * <code>sys.flow.eval({@literal <规则流ID>},{@literal <执行参数>},[附加上下文])</code>
 * </p>
 * 如果要使用当前规则的参数和上下文，可如下调用:<br>
 * <p>
 * <code>sys.flow.eval({@literal <规则流ID>},$,$$)</code>
 * </p>
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 * Created on 2022-08-26
 */
@Udf(group = "sys.flow", value = "eval")
@Slf4j
public class EvalFlowUdf implements Function2<String, Object, Object>, Function3<String, Object, Map<String, Object>, Object> {


    /**
     * 调用规则流，传入指定参数和上下文
     *
     * @param flowId  规则流ID
     * @param target  执行参数
     * @param context 执行上下文
     * @return 规则流调用结果
     */
    public Object apply(String flowId, Object target, Map<String, Object> context) {
        RuleSuite ruleSuite = RuleSuite.get();
        NodeResult result = ruleSuite.evalFlow(flowId, target, context);
        return result.getResult();
    }

    /**
     * 调用规则流，传入指定参数
     *
     * @param flowId 规则流ID
     * @param target  执行参数
     * @return 规则流调用结果
     */
    public Object apply(String flowId, Object target) {
        RuleSuite ruleSuite = RuleSuite.get();
        NodeResult result = ruleSuite.evalFlow(flowId, target);
        return result.getResult();
    }


}
