package com.productpackage.emgcallservice.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ErrorResponseTest {

    @Test
    @DisplayName("默认构造与 setter/getter：字段可读写")
    public void testDefaultCtor_and_setters_gettersWork() {
        final ErrorResponse r = new ErrorResponse();
        r.setErrorCode("EC");
        r.setMessage("m");
        r.setInvokeId("iid");

        assertThat(r.getErrorCode()).isEqualTo("EC");
        assertThat(r.getMessage()).isEqualTo("m");
        assertThat(r.getInvokeId()).isEqualTo("iid");
    }

    @Test
    @DisplayName("参数化构造：字段按构造器值返回")
    public void testAllArgsConstructor_fieldsSet() {
        final ErrorResponse r = new ErrorResponse("EC2", "mm", "ii2");
        assertThat(r.getErrorCode()).isEqualTo("EC2");
        assertThat(r.getMessage()).isEqualTo("mm");
        assertThat(r.getInvokeId()).isEqualTo("ii2");
    }
}

