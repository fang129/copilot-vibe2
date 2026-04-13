package com.productpackage.emgcallservice.exception;

/**
 * 业务异常，包含错误码与 http 状态，供全局异常处理器使用。
 */
public class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String errorCode;
    private final int httpStatus;

    public BusinessException(final String errorCode, final String message, final int httpStatus) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    public BusinessException(final String errorCode, final String message, final Throwable cause, final int httpStatus) {
        super(message, cause);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}

