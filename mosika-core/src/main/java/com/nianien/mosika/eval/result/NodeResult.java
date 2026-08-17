package com.nianien.mosika.eval.result;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 一次根节点执行的对外结果
 * <p>
 * 业务返回值和规则详情分别保存，详情为空不影响业务返回值
 *
 * Created on 2022/8/2
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
@Getter
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NodeResult {

    /**
     * 根节点表达式
     */
    private String expr;
    /**
     * 根节点业务返回值
     */
    private Object result;
    /**
     * 递归规则执行详情
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
