package com.productpackage.emgcallservice.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class ForwardResultTest {

    @Test
    @DisplayName("ForwardResult：getter/setter 与默认值生效")
    public void testGettersSetters_defaultValues_and_setters() {
        ForwardResult r = new ForwardResult();
        assertThat(r.getStatus()).isEqualTo(-1);

        r.setDownstreamName("DS");
        r.setStatus(200);
        r.setResponseBody(new byte[]{1,2,3});
        Map<String,String> headers = new HashMap<>(); headers.put("k","v");
        r.setResponseHeaders(headers);
        r.setExceptionOccurred(true);
        r.setTimeoutOccurred(false);
        r.setAttempts(2);

        assertThat(r.getDownstreamName()).isEqualTo("DS");
        assertThat(r.getStatus()).isEqualTo(200);
        assertThat(r.getResponseBody()).containsExactly((byte)1,(byte)2,(byte)3);
        assertThat(r.getResponseHeaders()).containsEntry("k","v");
        assertThat(r.isExceptionOccurred()).isTrue();
        assertThat(r.isTimeoutOccurred()).isFalse();
        assertThat(r.getAttempts()).isEqualTo(2);
    }
}

