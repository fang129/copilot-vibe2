package com.productpackage.emgcallservice.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.util.LinkedMultiValueMap;

import static org.assertj.core.api.Assertions.assertThat;

public class ForwardedRequestDataTest {

    @Test
    @DisplayName("ForwardedRequestData：getter/setter 工作并能处理 null 值")
    public void testGettersSetters_and_nullHandling() {
        ForwardedRequestData d = new ForwardedRequestData();
        assertThat(d.getHeaderMap()).isNull();
        assertThat(d.getBodyBin()).isNull();
        assertThat(d.getBodyMap()).isNull();
        assertThat(d.getQueryString()).isNull();
        assertThat(d.getRequestUri()).isNull();

        java.util.Map<String,String> hm = new java.util.HashMap<>();
        hm.put("h","v");
        d.setHeaderMap(hm);
        d.setBodyBin(new byte[]{9});
        LinkedMultiValueMap<String,String> m = new LinkedMultiValueMap<>();
        m.add("a","1");
        d.setBodyMap(m);
        d.setQueryString("q=1");
        d.setRequestUri("/x");

        assertThat(d.getHeaderMap()).containsEntry("h","v");
        assertThat(d.getBodyBin()).containsExactly((byte)9);
        assertThat(d.getBodyMap().getFirst("a")).isEqualTo("1");
        assertThat(d.getQueryString()).isEqualTo("q=1");
        assertThat(d.getRequestUri()).isEqualTo("/x");
    }
}


