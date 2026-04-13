package com.productpackage.emgcallservice.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BusinessExceptionTest {

    @Test
    @DisplayName("构造器：带 message 与 httpStatus，getter 返回期望值")
    public void testConstructor_messageAndStatus_gettersReturnValues() {
        final BusinessException ex = new BusinessException("E_CODE", "some error", 400);
        assertThat(ex.getErrorCode()).isEqualTo("E_CODE");
        assertThat(ex.getMessage()).isEqualTo("some error");
        assertThat(ex.getHttpStatus()).isEqualTo(400);
        assertThat(ex.getCause()).isNull();
    }

    @Test
    @DisplayName("构造器：带 cause 时，cause 被保存并可通过 getCause 获取")
    public void testConstructor_withCause_gettersAndCause() {
        final Throwable cause = new RuntimeException("root");
        final BusinessException ex = new BusinessException("E2", "msg", cause, 502);
        assertThat(ex.getErrorCode()).isEqualTo("E2");
        assertThat(ex.getMessage()).isEqualTo("msg");
        assertThat(ex.getHttpStatus()).isEqualTo(502);
        assertThat(ex.getCause()).isSameAs(cause);
    }
}

