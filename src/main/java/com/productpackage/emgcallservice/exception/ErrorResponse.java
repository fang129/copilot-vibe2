package com.productpackage.emgcallservice.exception;

/**
 * 错误响应结构（如需返回错误 body 可使用）。
 */
public class ErrorResponse {

    private String errorCode;
    private String message;
    private String invokeId;

    public ErrorResponse() {
        // default
    }

    public ErrorResponse(final String errorCode, final String message, final String invokeId) {
        this.errorCode = errorCode;
        this.message = message;
        this.invokeId = invokeId;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(final String errorCode) {
        this.errorCode = errorCode;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(final String message) {
        this.message = message;
    }

    public String getInvokeId() {
        return invokeId;
    }

    public void setInvokeId(final String invokeId) {
        this.invokeId = invokeId;
    }
}

