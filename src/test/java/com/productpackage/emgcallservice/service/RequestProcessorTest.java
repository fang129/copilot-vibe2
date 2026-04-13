package com.productpackage.emgcallservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.productpackage.emgcallservice.exception.BusinessException;
import com.productpackage.emgcallservice.model.ForwardResult;
import com.productpackage.emgcallservice.model.ForwardedRequestData;
import com.productpackage.emgcallservice.util.LogMessageService;
import com.productpackage.emgcallservice.util.UrlEncodedParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

import javax.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class RequestProcessorTest {

    @Mock
    private BehaviorLoader behaviorLoader;

    @Mock
    private UrlEncodedParser urlEncodedParser;

    @Mock
    private ForwarderService forwarderService;

    @Mock
    private LogMessageService logMessageService;

    @InjectMocks
    private RequestProcessor processor;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    public void setUp() {
        // processor will be constructed by @InjectMocks
    }

    @Test
    @DisplayName("findBehaviorForPath：未找到配置时抛 BusinessException")
    public void testFindBehaviorForPath_notFound_throwBusinessException() {
        when(behaviorLoader.findBehaviorForPath("/x")).thenReturn(Optional.empty());
        byte[] raw = "payload".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> processor.findBehaviorForPath("/x", raw)).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("findBehaviorForPath：找到匹配节点时返回 JsonNode")
    public void testFindBehaviorForPath_found_returnNode() throws Exception {
        JsonNode node = objectMapper.readTree("{\"RewriteUrl\":\"/ok\"}");
        when(behaviorLoader.findBehaviorForPath("/a")).thenReturn(Optional.of(node));
        JsonNode out = processor.findBehaviorForPath("/a", null);
        assertThat(out.path("RewriteUrl").asText()).isEqualTo("/ok");
    }

    @Test
    @DisplayName("createForwardedRequest：正确收集 headers、query 与 uri，并调用 body 转换")
    public void testCreateForwardedRequest_headersAndUris_and_body_transformed() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("h1", "v1");
        req.addHeader("h1", "v2");
        req.setQueryString("a=1");
        req.setRequestURI("/path");

        JsonNode behavior = objectMapper.createObjectNode().put("Pattern", "X");

        when(urlEncodedParser.isValidUrlEncoded("k=1")).thenReturn(true);
        when(urlEncodedParser.parseToOrderedList("k=1")).thenReturn(Arrays.asList(new UrlEncodedParser.Pair("k","1")));
        when(urlEncodedParser.applyCoordinateRules(org.mockito.Mockito.anyList(), org.mockito.Mockito.anyString(), org.mockito.Mockito.anyString()))
                .thenReturn(Arrays.asList(new UrlEncodedParser.Pair("k","999.9999999")));
        when(urlEncodedParser.serialize(org.mockito.Mockito.anyList())).thenReturn("k=999.9999999");

        byte[] raw = "k=1".getBytes(StandardCharsets.UTF_8);
        ForwardedRequestData forward = processor.createForwardedRequest(req, raw, behavior);
        assertThat(forward.getHeaderMap()).containsKey("h1");
        assertThat(forward.getQueryString()).isEqualTo("a=1");
        assertThat(new String(forward.getBodyBin(), StandardCharsets.UTF_8)).isEqualTo("k=999.9999999");
    }

    @Test
    @DisplayName("transformBodyByPattern：pattern 为 D 时返回原始二进制")
    public void testTransformBodyByPattern_patternD_returnRaw() {
        byte[] raw = new byte[]{1,2,3};
        byte[] out = processor.transformBodyByPattern("D", raw);
        assertThat(out).isSameAs(raw);
    }

    @Test
    @DisplayName("transformBodyByPattern：null/空 body 时透传原值")
    public void testTransformBodyByPattern_nullOrEmptyBody_returnAsIs() {
        assertThat(processor.transformBodyByPattern("X", null)).isNull();
        assertThat(processor.transformBodyByPattern("X", new byte[0])).isEqualTo(new byte[0]);
    }

    @Test
    @DisplayName("transformBodyByPattern：urlencoded 可解析时走 parse->applyCoordinateRules->serialize 路径")
    public void testTransformBodyByPattern_urlencoded_valid_parsedAndSerialized() {
        when(urlEncodedParser.isValidUrlEncoded(org.mockito.Mockito.anyString())).thenReturn(true);
        when(urlEncodedParser.parseToOrderedList(org.mockito.Mockito.anyString())).thenReturn(Arrays.asList(new UrlEncodedParser.Pair("k","1")));
        when(urlEncodedParser.applyCoordinateRules(org.mockito.Mockito.anyList(), org.mockito.Mockito.anyString(), org.mockito.Mockito.anyString()))
                .thenReturn(Arrays.asList(new UrlEncodedParser.Pair("k","999.9999999")));
        when(urlEncodedParser.serialize(org.mockito.Mockito.anyList())).thenReturn("k=999.9999999");

        byte[] raw = "k=1".getBytes(StandardCharsets.UTF_8);
        byte[] out = processor.transformBodyByPattern("X", raw);
        assertThat(new String(out, StandardCharsets.UTF_8)).isEqualTo("k=999.9999999");
    }

    @Test
    @DisplayName("process：缺少 RewriteUrl 时抛 BusinessException 并记录错误")
    public void testProcess_missingRewriteUrl_throwBusinessException() {
        JsonNode behavior = objectMapper.createObjectNode(); // no RewriteUrl
        when(behaviorLoader.findBehaviorForPath(org.mockito.Mockito.anyString())).thenReturn(Optional.of(behavior));

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI("/x");
        byte[] raw = "body".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> processor.process(req, raw)).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("process：forward 返回异常标记时返回该 ForwardResult")
    public void testProcess_forwarderException_returnForwardResultWithExceptionOccurred() {
        JsonNode behavior = objectMapper.createObjectNode().put("RewriteUrl","http://a");
        when(behaviorLoader.findBehaviorForPath(org.mockito.Mockito.anyString())).thenReturn(Optional.of(behavior));

        ForwardedRequestData fwd = new ForwardedRequestData();
        // lenient stub: transformBodyByPattern may not call urlEncodedParser when pattern == "D"
        org.mockito.Mockito.lenient().when(urlEncodedParser.isValidUrlEncoded(org.mockito.Mockito.anyString())).thenReturn(false);

        ForwardResult fr = new ForwardResult();
        fr.setExceptionOccurred(true);
        when(forwarderService.forward(org.mockito.Mockito.anyString(), org.mockito.Mockito.anyString(), org.mockito.Mockito.any(), org.mockito.Mockito.anyInt(), org.mockito.Mockito.anyInt(), org.mockito.Mockito.anyInt()))
                .thenReturn(fr);

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI("/p");
        byte[] raw = "b".getBytes(StandardCharsets.UTF_8);

        ForwardResult out = processor.process(req, raw);
        assertThat(out).isSameAs(fr);
    }

    @Test
    @DisplayName("forwardRequest：委托给 ForwarderService.forward 并返回其结果")
    public void testForwardRequest_delegatesToForwarderService() {
        ForwardedRequestData d = new ForwardedRequestData();
        ForwardResult expected = new ForwardResult();
        when(forwarderService.forward("ECDP", "u", d, 1000, 0, 100)).thenReturn(expected);
        ForwardResult out = processor.forwardRequest("ECDP", "u", d, 1000, 0, 100);
        assertThat(out).isSameAs(expected);
    }
}


