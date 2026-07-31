package com.skyfalling.mousika.exception;

import lombok.Getter;

/**
 * 没有匹配的规则
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 * Created on 2021-11-19
 */
@Getter
public class RuleMatchException extends RuntimeException {
    private final String ruleId;

    /**
     * @param ruleId  规则ID
     * @param message 异常信息
     * @param e       原始异常
     */
    public RuleMatchException(String ruleId, String message, Throwable e) {
        super(message, e);
        this.ruleId = ruleId;
    }

    /**
     * @param ruleId  规则ID
     * @param message 异常信息
     */
    public RuleMatchException(String ruleId, String message) {
        super(message);
        this.ruleId = ruleId;
    }
}
