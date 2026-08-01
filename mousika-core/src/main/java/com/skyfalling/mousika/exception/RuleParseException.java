package com.skyfalling.mousika.exception;

import lombok.Getter;

/**
 * 规则解析异常
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 * Created on 2021-11-19
 */
@Getter
public class RuleParseException extends RuntimeException {
    private final String expr;

    /**
     *
     */
    public RuleParseException(String expr, String message, Throwable e) {
        super(message, e);
        this.expr = expr;
    }

    public RuleParseException(String expr, String message) {
        super(message);
        this.expr = expr;
    }
}
