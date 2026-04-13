package com.productpackage.emgcallservice.model;

import java.util.Map;

/**
 * 转发结果结构，用于上层决策。
 */
public class ForwardResult {

    private String downstreamName;
    private int status = -1;
    private byte[] responseBody;
    private Map<String, String> responseHeaders;
    private boolean exceptionOccurred;
    private boolean timeoutOccurred;
    private int attempts;

    public String getDownstreamName() {
        return downstreamName;
    }

    public void setDownstreamName(final String downstreamName) {
        this.downstreamName = downstreamName;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(final int status) {
        this.status = status;
    }

    public byte[] getResponseBody() {
        return responseBody;
    }

    public void setResponseBody(final byte[] responseBody) {
        this.responseBody = responseBody;
    }

    public Map<String, String> getResponseHeaders() {
        return responseHeaders;
    }

    public void setResponseHeaders(final Map<String, String> responseHeaders) {
        this.responseHeaders = responseHeaders;
    }

    public boolean isExceptionOccurred() {
        return exceptionOccurred;
    }

    public void setExceptionOccurred(final boolean exceptionOccurred) {
        this.exceptionOccurred = exceptionOccurred;
    }

    public boolean isTimeoutOccurred() {
        return timeoutOccurred;
    }

    public void setTimeoutOccurred(final boolean timeoutOccurred) {
        this.timeoutOccurred = timeoutOccurred;
    }

    public int getAttempts() {
        return attempts;
    }

    public void setAttempts(final int attempts) {
        this.attempts = attempts;
    }
}

