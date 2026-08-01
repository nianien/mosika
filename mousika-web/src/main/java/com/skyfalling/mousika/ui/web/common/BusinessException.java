package com.skyfalling.mousika.ui.web.common;

import lombok.Getter;

/**
 * 业务层可预期异常，携带明确的错误码与消息。
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
@Getter
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }
}
