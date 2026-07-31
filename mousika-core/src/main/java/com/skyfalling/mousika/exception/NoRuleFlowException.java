package com.skyfalling.mousika.exception;

import lombok.Getter;

/**
 * 没有匹配的规则流
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 * Created on 2021-11-19
 */
@Getter
public class NoRuleFlowException extends RuntimeException {
    private final String flowId;

    /**
     * @param flowId  规则流ID
     * @param message 异常信息
     * @param e       原始异常
     */
    public NoRuleFlowException(String flowId, String message, Throwable e) {
        super(message, e);
        this.flowId = flowId;
    }

    /**
     * @param flowId  规则流ID
     * @param message 异常信息
     */
    public NoRuleFlowException(String flowId, String message) {
        super(message);
        this.flowId = flowId;
    }
}
