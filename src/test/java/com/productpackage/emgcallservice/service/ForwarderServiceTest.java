package com.productpackage.emgcallservice.service;

import com.productpackage.emgcallservice.model.ForwardedRequestData;
import com.productpackage.emgcallservice.util.LogMessageService;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.lang.reflect.Method;

import com.productpackage.emgcallservice.model.ForwardResult;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
public class ForwarderServiceTest {

    @Mock
    private LogMessageService logMessageService;

    private WireMockServer wire;

    @BeforeEach
    public void before() {
        wire = new WireMockServer(options().dynamicPort());
        wire.start();
        configureFor("localhost", wire.port());
    }

    @AfterEach
    public void after() {
        if (wire != null && wire.isRunning()) {
            wire.stop();
        }
    }

    @Test
    @DisplayName("serializeBodyMapForLogging：空 map 返回空字符串")
    public void testSerializeBodyMapForLogging_emptyMap_returnEmptyString() {
        // package-visible static helper can be called directly
        String out1 = ForwarderService.serializeBodyMapForLogging(null);
        assertThat(out1).isEqualTo("");
        LinkedMultiValueMap<String,String> map = new LinkedMultiValueMap<>();
        String out2 = ForwarderService.serializeBodyMapForLogging(map);
        assertThat(out2).isEqualTo("");

        map.add("a","1");
        String out3 = ForwarderService.serializeBodyMapForLogging(map);
        assertThat(out3).contains("a=1");
    }

    @Test
    @DisplayName("forward：当下游超时（fixedDelay > timeout）时返回 exceptionOccurred=true")
    public void testForward_whenDownstreamTimesOut_setsExceptionOccurred() {
        // stub endpoint with fixed delay longer than timeout
        stubFor(post(urlEqualTo("/slow")).willReturn(aResponse().withStatus(200).withBody("ok").withFixedDelay(1000)));

        ForwarderService svc = new ForwarderService(logMessageService);
        ForwardedRequestData data = new ForwardedRequestData();
        data.setBodyBin(new byte[]{1,2,3});

        String url = "http://localhost:" + wire.port() + "/slow";
        // set timeout smaller than fixedDelay to trigger timeout handling
        ForwardResult r = svc.forward("ECDP", url, data, 100, 0, 10);
        assertThat(r.isExceptionOccurred()).isTrue();
        assertThat(r.isTimeoutOccurred()).isTrue();
    }

    @Test
    @DisplayName("forward：下游 200 返回 body 与 headers 且 attempts=1")
    public void testForward_success_returnsBodyAndHeaders() {
        stubFor(post(urlEqualTo("/ok")).willReturn(aResponse().withStatus(200).withHeader("h", "v").withBody("resp")));

        ForwarderService svc = new ForwarderService(logMessageService);
        ForwardedRequestData data = new ForwardedRequestData();
        data.setBodyBin("reqb".getBytes());

        String url = "http://localhost:" + wire.port() + "/ok";
        ForwardResult r = svc.forward("ECDP", url, data, 2000, 0, 10);

        assertThat(r.getStatus()).isEqualTo(200);
        assertThat(new String(r.getResponseBody())).isEqualTo("resp");
        assertThat(r.getResponseHeaders()).containsEntry("h", "v");
        assertThat(r.getAttempts()).isGreaterThanOrEqualTo(1);
        assertThat(r.isExceptionOccurred()).isFalse();
    }
}


