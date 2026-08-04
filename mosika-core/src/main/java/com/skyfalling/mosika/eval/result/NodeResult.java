package com.skyfalling.mosika.eval.result;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 节点执行结果, 规则集的分支结果
 * Created on 2022/8/2
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
@Getter
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NodeResult {

    /**
     * 节点表达式
     */
    private String expr;
    /**
     * 是否匹配
     */
    private Object result;
    /**
     * 节点评估结果
     */
    private List<RuleResult> details;


    @Override
    public String toString() {
        return "NodeResult("
                + "expr=" + expr
                + ", result=" + result
                + ", details=" + details
                + ')';
    }
}
