package com.productpackage.emgcallservice.service;

import com.productpackage.emgcallservice.model.ForwardedRequestData;
import com.productpackage.emgcallservice.util.LogMessageService;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.message.BasicHeader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.List;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.lang.reflect.Method;

import com.productpackage.emgcallservice.model.ForwardResult;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
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

    @Test
    @DisplayName("forward：第一次 500 第二次 200，重试成功 attempts=2")
    public void testForward_retrySucceeds_attemptsTwo() {
        stubFor(post(urlEqualTo("/retry")).inScenario("retry-scenario")
                .whenScenarioStateIs(STARTED).willReturn(aResponse().withStatus(500)).willSetStateTo("second"));
        stubFor(post(urlEqualTo("/retry")).inScenario("retry-scenario")
                .whenScenarioStateIs("second").willReturn(aResponse().withStatus(200).withBody("ok2")));

        ForwarderService svc = new ForwarderService(logMessageService);
        ForwardedRequestData data = new ForwardedRequestData();
        data.setBodyBin(new byte[]{9});

        String url = "http://localhost:" + wire.port() + "/retry";
        ForwardResult r = svc.forward("ECDP", url, data, 2000, 1, 10);
        assertThat(r.getStatus()).isEqualTo(200);
        assertThat(r.getAttempts()).isEqualTo(2);
        assertThat(r.isExceptionOccurred()).isFalse();
    }

    @Test
    @DisplayName("forward：非2xx 且不重试时，返回 non-2xx 状态并记录错误")
    public void testForward_non2xx_exhausted_returnsStatus() {
        stubFor(post(urlEqualTo("/always500")).willReturn(aResponse().withStatus(500).withBody("err")));

        ForwarderService svc = new ForwarderService(logMessageService);
        ForwardedRequestData data = new ForwardedRequestData();
        data.setBodyBin(new byte[]{3});

        String url = "http://localhost:" + wire.port() + "/always500";
        ForwardResult r = svc.forward("ECDP", url, data, 2000, 0, 10);

        assertThat(r.getStatus()).isEqualTo(500);
        assertThat(r.isExceptionOccurred()).isFalse();
        Mockito.verify(logMessageService).format(Mockito.eq("log.apiCallFailedResponseNon200"), Mockito.any(), Mockito.eq("ECDP"), Mockito.eq(url), Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    @DisplayName("forward：headers 过滤与 null 跳过")
    public void testForward_headerFiltering_andNulls() {
        stubFor(post(urlEqualTo("/hdr")).willReturn(aResponse().withStatus(200).withBody("h")));

        ForwarderService svc = new ForwarderService(logMessageService);
        ForwardedRequestData data = new ForwardedRequestData();
        java.util.Map<String,String> hm = new java.util.HashMap<>();
        hm.put(null, "v0");
        hm.put("Connection", "shouldBeFiltered");
        hm.put("Host", "shouldBeFiltered");
        hm.put("X-OK", "v1");
        hm.put("X-Null", null);
        data.setHeaderMap(hm);
        data.setBodyBin(new byte[]{1});

        String url = "http://localhost:" + wire.port() + "/hdr";
        ForwardResult r = svc.forward("ECDP", url, data, 2000, 0, 10);
        assertThat(r.getStatus()).isEqualTo(200);

        List<LoggedRequest> reqs = findAll(postRequestedFor(urlEqualTo("/hdr")));
        assertThat(reqs).hasSize(1);
        LoggedRequest lr = reqs.get(0);
        assertThat(lr.getHeader("X-OK")).isEqualTo("v1");
        // WireMock / HTTP may set Connection header; ensure our original value was not forwarded
        assertThat(lr.getHeader("Connection")).isNotEqualTo("shouldBeFiltered");
        // Host header may be set by the HTTP client; ensure our original Host value was not forwarded
        assertThat(lr.getHeader("Host")).isNotEqualTo("shouldBeFiltered");
        assertThat(lr.getHeader("X-Null")).isNull();
    }

    @Test
    @DisplayName("forward：bodyMap 路径 - 多值和 null 值序列化")
    public void testForward_bodyMap_serialization() {
        stubFor(post(urlEqualTo("/bmap")).willReturn(aResponse().withStatus(200).withBody("ok")));

        ForwarderService svc = new ForwarderService(logMessageService);
        ForwardedRequestData data = new ForwardedRequestData();
        MultiValueMap<String,String> m = new LinkedMultiValueMap<>();
        m.add("a","1");
        m.add("a","2");
        m.add("b", null);
        data.setBodyBin(null);
        data.setBodyMap(m);

        String url = "http://localhost:" + wire.port() + "/bmap";
        ForwardResult r = svc.forward("ECDP", url, data, 2000, 0, 10);
        assertThat(r.getStatus()).isEqualTo(200);

        List<LoggedRequest> reqs = findAll(postRequestedFor(urlEqualTo("/bmap")));
        assertThat(reqs).hasSize(1);
        LoggedRequest lr = reqs.get(0);
        String body = lr.getBodyAsString();
        assertThat(body).contains("a=1");
        assertThat(body).contains("a=2");
        assertThat(body).contains("b=");
    }

    @Test
    @DisplayName("forward：execute 抛出异常并在 sleep 中被中断，触发 InterruptedException 分支")
    public void testForward_executeThrows_andSleepInterrupted_triggersInterruptedBranch() throws Exception {
        // We'll create a ForwarderService subclass to inject a mock client that throws IOException and a sleepMillis that throws InterruptedException
        CloseableHttpClient mockClient = Mockito.mock(CloseableHttpClient.class);
        Mockito.when(mockClient.execute(Mockito.any(org.apache.http.client.methods.HttpPost.class))).thenThrow(new IOException("boom"));

        ForwarderService svc = new ForwarderService(logMessageService) {
            @Override
            CloseableHttpClient createHttpClient(final org.apache.http.client.config.RequestConfig requestConfig) {
                return mockClient;
            }

            @Override
            void sleepMillis(final long ms) throws InterruptedException {
                throw new InterruptedException("test-interrupt");
            }
        };

        ForwardedRequestData data = new ForwardedRequestData();
        data.setBodyBin(new byte[]{7});

        String url = "http://localhost:" + wire.port() + "/willnotbeused";
        ForwardResult r = svc.forward("ECDP", url, data, 2000, 1, 10);

        assertThat(r.isExceptionOccurred()).isTrue();
        assertThat(r.getStatus()).isEqualTo(-1);
        Mockito.verify(logMessageService, Mockito.atLeastOnce()).format(Mockito.eq("log.apiCallUnexpectedError"), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
        // clear interrupted flag so test infrastructure (e.g. WireMock.stop) is not affected
        if (Thread.currentThread().isInterrupted()) {
            Thread.interrupted();
        }
    }

    @Test
    @DisplayName("forward（mock client）：验证 request header 过滤、null 跳过 与 post headers 设置")
    public void testForward_withMockClient_headerFiltering_andPostHeaders() throws Exception {
        // build mock response
        CloseableHttpResponse mockResp = Mockito.mock(CloseableHttpResponse.class);
        StatusLine mockStatus = Mockito.mock(StatusLine.class);
        Mockito.when(mockStatus.getStatusCode()).thenReturn(200);
        Mockito.when(mockResp.getStatusLine()).thenReturn(mockStatus);
        HttpEntity mockEntity = Mockito.mock(HttpEntity.class);
        byte[] respBytes = "resp-mock".getBytes();
        Mockito.when(mockEntity.getContent()).thenReturn(new java.io.ByteArrayInputStream(respBytes));
        Mockito.when(mockResp.getEntity()).thenReturn(mockEntity);
        Header[] respHeaders = new Header[]{new BasicHeader("Content-Length", "10"), new BasicHeader("X-OK", "v")};
        Mockito.when(mockResp.getAllHeaders()).thenReturn(respHeaders);

        // capture posts
        final List<Object> captured = new ArrayList<>();
        CloseableHttpClient mockClient = Mockito.mock(CloseableHttpClient.class);
        Mockito.doAnswer(invocation -> {
            captured.add(invocation.getArgument(0));
            return mockResp;
        }).when(mockClient).execute(Mockito.any());

        Map<String, String> reqHeaders = new HashMap<>();
        reqHeaders.put(null, "v0");
        reqHeaders.put("Connection", "filtered");
        reqHeaders.put("Host", "filtered");
        reqHeaders.put("X-Null", null);
        reqHeaders.put("X-Fwd", "yes");

        ForwardedRequestData data = new ForwardedRequestData();
        data.setBodyBin("reqb".getBytes());
        data.setHeaderMap(reqHeaders);

        Mockito.when(logMessageService.format(Mockito.anyString(), Mockito.any(), Mockito.any(), Mockito.any())).thenReturn("f");

        ForwarderService svc = new ForwarderService(logMessageService) {
            @Override
            CloseableHttpClient createHttpClient(final org.apache.http.client.config.RequestConfig requestConfig) {
                return mockClient;
            }

            @Override
            void sleepMillis(final long ms) {
                // no sleep
            }
        };

        ForwardResult r = svc.forward("down", "http://unused", data, 1000, 0, 1);
        assertThat(r.getStatus()).isEqualTo(200);
        assertThat(r.getResponseBody()).isNotNull();
        assertThat(captured).hasSize(1);
        org.apache.http.client.methods.HttpPost post = (org.apache.http.client.methods.HttpPost) captured.get(0);
        boolean foundXfwd = false;
        boolean foundHost = false;
        for (org.apache.http.Header h : post.getAllHeaders()) {
            if ("X-Fwd".equalsIgnoreCase(h.getName())) foundXfwd = true;
            if ("Host".equalsIgnoreCase(h.getName())) foundHost = true;
        }
        assertThat(foundXfwd).isTrue();
        assertThat(foundHost).isFalse();
    }

    @Test
    @DisplayName("forward（mock client）：响应头名为 null 时应跳过该 header")
    public void testForward_responseHeaderNameNull_filteredOut() throws Exception {
        CloseableHttpResponse mockResp = Mockito.mock(CloseableHttpResponse.class);
        StatusLine mockStatus = Mockito.mock(StatusLine.class);
        Mockito.when(mockStatus.getStatusCode()).thenReturn(200);
        Mockito.when(mockResp.getStatusLine()).thenReturn(mockStatus);
        HttpEntity mockEntity = Mockito.mock(HttpEntity.class);
        Mockito.when(mockEntity.getContent()).thenReturn(new java.io.ByteArrayInputStream("b".getBytes()));
        Mockito.when(mockResp.getEntity()).thenReturn(mockEntity);

        Header nullNameHeader = Mockito.mock(Header.class);
        Mockito.when(nullNameHeader.getName()).thenReturn(null);
        // value not needed because name == null branch should skip reading value
        Header ok = new BasicHeader("X-OK", "v2");
        Mockito.when(mockResp.getAllHeaders()).thenReturn(new Header[]{nullNameHeader, ok});

        CloseableHttpClient mockClient = Mockito.mock(CloseableHttpClient.class);
        Mockito.when(mockClient.execute(Mockito.any())).thenReturn(mockResp);

        ForwardedRequestData data = new ForwardedRequestData();
        data.setBodyBin(new byte[]{1});

        ForwarderService svc = new ForwarderService(logMessageService) {
            @Override
            CloseableHttpClient createHttpClient(final org.apache.http.client.config.RequestConfig requestConfig) {
                return mockClient;
            }

            @Override
            void sleepMillis(final long ms) {}
        };

        ForwardResult r = svc.forward("d", "http://unused", data, 1000, 0, 1);
        assertThat(r.getResponseHeaders()).doesNotContainKey(null);
        assertThat(r.getResponseHeaders()).containsEntry("X-OK", "v2");
    }

    @Test
    @DisplayName("forward：retryCount < 0 时不进入循环，直接返回异常结果（未拿到响应）")
    public void testForward_retryCountNegative_setsExceptionOccurred() {
        ForwarderService svc = new ForwarderService(logMessageService);
        ForwardedRequestData data = new ForwardedRequestData();
        data.setBodyBin(new byte[]{1});

        ForwardResult r = svc.forward("d", "http://unused", data, 1000, -1, 1);
        assertThat(r.isExceptionOccurred()).isTrue();
        assertThat(r.getStatus()).isEqualTo(-1);
    }

    @Test
    @DisplayName("forward（mock client）：当响应实体为 null 时，应返回空 body")
    public void testForward_responseEntityNull_returnsEmptyBody() throws Exception {
        CloseableHttpResponse mockResp = Mockito.mock(CloseableHttpResponse.class);
        StatusLine mockStatus = Mockito.mock(StatusLine.class);
        Mockito.when(mockStatus.getStatusCode()).thenReturn(200);
        Mockito.when(mockResp.getStatusLine()).thenReturn(mockStatus);
        Mockito.when(mockResp.getEntity()).thenReturn(null);
        Header ok = new BasicHeader("X-OK", "v2");
        Mockito.when(mockResp.getAllHeaders()).thenReturn(new Header[]{ok});

        CloseableHttpClient mockClient = Mockito.mock(CloseableHttpClient.class);
        Mockito.when(mockClient.execute(Mockito.any())).thenReturn(mockResp);

        ForwardedRequestData data = new ForwardedRequestData();
        data.setBodyBin(new byte[]{1});

        ForwarderService svc = new ForwarderService(logMessageService) {
            @Override
            CloseableHttpClient createHttpClient(final org.apache.http.client.config.RequestConfig requestConfig) {
                return mockClient;
            }

            @Override
            void sleepMillis(final long ms) {}
        };

        ForwardResult r = svc.forward("d", "http://unused", data, 1000, 0, 1);
        assertThat(r.getStatus()).isEqualTo(200);
        assertThat(r.getResponseBody()).isNotNull();
        assertThat(r.getResponseBody().length).isEqualTo(0);
    }

    @Test
    @DisplayName("forward（mock client）：状态码 199 被视为非2xx 并返回原始状态")
    public void testForward_status199_non2xx_logsAndReturns() throws Exception {
        CloseableHttpResponse mockResp = Mockito.mock(CloseableHttpResponse.class);
        StatusLine mockStatus = Mockito.mock(StatusLine.class);
        Mockito.when(mockStatus.getStatusCode()).thenReturn(199);
        Mockito.when(mockResp.getStatusLine()).thenReturn(mockStatus);
        HttpEntity mockEntity = Mockito.mock(HttpEntity.class);
        Mockito.when(mockEntity.getContent()).thenReturn(new java.io.ByteArrayInputStream("x".getBytes()));
        Mockito.when(mockResp.getEntity()).thenReturn(mockEntity);
        Mockito.when(mockResp.getAllHeaders()).thenReturn(new Header[]{});

        CloseableHttpClient mockClient = Mockito.mock(CloseableHttpClient.class);
        Mockito.when(mockClient.execute(Mockito.any())).thenReturn(mockResp);

        ForwardedRequestData data = new ForwardedRequestData();
        data.setBodyBin(new byte[]{9});

        ForwarderService svc = new ForwarderService(logMessageService) {
            @Override
            CloseableHttpClient createHttpClient(final org.apache.http.client.config.RequestConfig requestConfig) {
                return mockClient;
            }

            @Override
            void sleepMillis(final long ms) {}
        };

        ForwardResult r = svc.forward("d", "http://unused", data, 1000, 0, 1);
        assertThat(r.getStatus()).isEqualTo(199);
        // non-2xx final return should not mark exceptionOccurred
        assertThat(r.isExceptionOccurred()).isFalse();
        Mockito.verify(logMessageService, Mockito.atLeastOnce()).format(Mockito.eq("log.apiCallFailedResponseNon200"), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    @DisplayName("sleepMillis：直接调用实际方法应当能休眠（覆盖 Thread.sleep 分支）")
    public void testSleepMillis_actualSleep() throws Exception {
        ForwarderService svc = new ForwarderService(logMessageService);
        // should not throw
        svc.sleepMillis(1);
    }

    @Test
    @DisplayName("forward：当第一次 execute 抛异常且可重试时，应调用 sleepMillis 再重试")
    public void testForward_callsSleepMillis_whenRetrying() throws Exception {
        CloseableHttpClient mockClient = Mockito.mock(CloseableHttpClient.class);
        // first call throws, second call also throws (we just want to exercise sleepMillis call)
        Mockito.when(mockClient.execute(Mockito.any(org.apache.http.client.methods.HttpPost.class)))
                .thenThrow(new IOException("boom1")).thenThrow(new IOException("boom2"));

        final java.util.concurrent.atomic.AtomicBoolean slept = new java.util.concurrent.atomic.AtomicBoolean(false);

        ForwarderService svc = new ForwarderService(logMessageService) {
            @Override
            CloseableHttpClient createHttpClient(final org.apache.http.client.config.RequestConfig requestConfig) {
                return mockClient;
            }

            @Override
            void sleepMillis(final long ms) throws InterruptedException {
                slept.set(true);
                // don't actually sleep
            }
        };

        ForwardedRequestData data = new ForwardedRequestData();
        data.setBodyBin(new byte[]{3});

        ForwardResult r = svc.forward("d", "http://unused", data, 1000, 1, 1);
        assertThat(slept.get()).isTrue();
        assertThat(r.isExceptionOccurred()).isTrue();
    }
}


