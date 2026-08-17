package com.nianien.mosika.exception;

import lombok.Getter;

/**
 * 规则未注册或执行失败时抛出的评估异常
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 * Created on 2021-11-19
 */
@Getter
public class RuleEvalException extends RuntimeException {

    /**
     * 执行失败的规则 ID
     */
    private final String ruleId;

    /**
     * 创建包含原始异常的规则评估异常
     *
     * @param ruleId  规则 ID
     * @param message 异常信息
     * @param e       原始异常
     */
    public RuleEvalException(String ruleId, String message, Throwable e) {
        super(message, e);
        this.ruleId = ruleId;
    }

    /**
     * 创建规则评估异常
     *
     * @param ruleId  规则 ID
     * @param message 异常信息
     */
    public RuleEvalException(String ruleId, String message) {
        super(message);
        this.ruleId = ruleId;
    }
}
