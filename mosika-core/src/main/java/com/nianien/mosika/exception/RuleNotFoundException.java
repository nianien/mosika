package com.nianien.mosika.exception;

/**
 * 规则未注册时抛出的评估异常
 * <p>
 * 继承 {@link RuleEvalException}，与脚本执行失败区分开：
 * 前者是规则标识在当前套件中不存在，后者是规则已注册但执行出错
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
public class RuleNotFoundException extends RuleEvalException {

    /**
     * 创建规则未注册异常
     *
     * @param ruleId 未注册的规则 ID
     */
    public RuleNotFoundException(String ruleId) {
        super(ruleId, "unregistered rule:" + ruleId);
    }
}
