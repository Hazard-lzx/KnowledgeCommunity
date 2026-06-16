package com.knowledgecommunity.common;

import lombok.Getter;

/**
 * 业务异常：用于业务逻辑中主动抛出的可预期异常
 * 携带错误码和错误信息，由 GlobalExceptionHandler 统一处理
 */
@Getter
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(String message) {
        super(message);
        this.code = 500;
    }
}
