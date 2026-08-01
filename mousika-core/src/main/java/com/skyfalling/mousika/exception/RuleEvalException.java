package com.skyfalling.mousika.exception;

import lombok.Getter;

/**
 * 规则评估异常
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 * Created on 2021-11-19
 */
@Getter
public class RuleEvalException extends RuntimeException {
    private final String ruleId;

    /**
     *
     */
    public RuleEvalException(String ruleId, String message, Throwable e) {
        super(message, e);
        this.ruleId = ruleId;
    }

    public RuleEvalException(String ruleId, String message) {
        super(message);
        this.ruleId = ruleId;
    }
}
