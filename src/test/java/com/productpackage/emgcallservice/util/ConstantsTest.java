package com.productpackage.emgcallservice.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ConstantsTest {

    @Test
    @DisplayName("Constants：关键常量值未被意外修改")
    public void testConstants_values_expected() {
        assertThat(Constants.BUSINESS_CODE).isEqualTo("MCMOD_EMGCA");
        assertThat(Constants.DEFAULT_CONTENT_TYPE_FORWARD).isEqualTo("application/x-www-form-urlencoded");
        assertThat(Constants.LOG_PARAM_MAX_LENGTH).isEqualTo(4096);
        assertThat(Constants.RESPONSE_HEADERS_TO_EXCLUDE).contains("connection");
    }
}

