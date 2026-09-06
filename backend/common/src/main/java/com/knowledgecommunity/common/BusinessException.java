package com.knowledgecommunity.common;

import lombok.Getter;

/**
 * 业务异常：携带业务错误码，由 GlobalExceptionHandler 统一转为 Result
 */
@Getter
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(String message) {
        this(500, message);
    }
}
