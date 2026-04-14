package com.productpackage.emgcallservice.controller;

import com.productpackage.emgcallservice.model.ForwardResult;
import com.productpackage.emgcallservice.service.RequestProcessor;
import com.productpackage.emgcallservice.util.LogMessageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import java.util.UUID;

/**
 * Unit tests for EmgController to cover branches that are hard to reach via MockMvc.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EmgController 单元测试（纯单元）")
public class EmgControllerUnitTest {

    @Mock
    private RequestProcessor requestProcessor;

    @Mock
    private LogMessageService logMessageService;

    @InjectMocks
    private EmgController controller;

    @Test
    @DisplayName("handleAll：传入 X-Request-Id 时复用并返回默认 200 与空 body")
    void testHandleAll_reusesInvokeId_andDefaultStatus_andEmptyBody() throws Exception {
        final HttpServletRequest req = mock(HttpServletRequest.class);
        final HttpServletResponse resp = mock(HttpServletResponse.class);

        when(req.getHeader("X-Request-Id")).thenReturn("my-invoke-id");
        when(req.getMethod()).thenReturn("POST");
        when(req.getRequestURI()).thenReturn("/unit");
        when(logMessageService.format(any(), any())).thenReturn("msg");

        final ForwardResult fr = new ForwardResult(); // default status -1 -> treated as 200
        fr.setResponseBody(null);
        when(requestProcessor.process(any(), any())).thenReturn(fr);

        final ResponseEntity<?> respEntity = controller.handleAll(req, null, resp);

        assertThat(respEntity.getStatusCodeValue()).isEqualTo(200);
        assertThat(respEntity.getHeaders().containsKey("X-Request-Id")).isTrue();
        assertThat(respEntity.getHeaders().getFirst("X-Request-Id")).isEqualTo("my-invoke-id");
        // body should be empty byte[] when ForwardResult.responseBody == null
        assertThat(respEntity.getBody()).isInstanceOf(byte[].class);
        assertThat(((byte[]) respEntity.getBody()).length).isEqualTo(0);
    }

    @Test
    @DisplayName("handleAll：响应头包含 null key 或 null value，且存在 Content-Type 时不被覆盖")
    void testHandleAll_responseHeaders_withNullKeyAndNullValue_andContentTypePreserved() throws Exception {
        final HttpServletRequest req = mock(HttpServletRequest.class);
        final HttpServletResponse resp = mock(HttpServletResponse.class);

        when(req.getHeader("X-Request-Id")).thenReturn(null); // force controller to generate
        when(req.getMethod()).thenReturn("POST");
        when(req.getRequestURI()).thenReturn("/unit2");
        when(logMessageService.format(any(), any())).thenReturn("msg");

        final ForwardResult fr = new ForwardResult();
        // set status <= 0 so controller will treat as 200
        fr.setStatus(0);
        fr.setResponseBody(null);
        final Map<String, String> headers = new LinkedHashMap<>();
        headers.put(null, "v1"); // null key -> should be skipped
        headers.put("X-Null-Value", null); // null value -> should be skipped
        headers.put("Content-Type", "application/json"); // should be preserved
        headers.put("X-Ok", "v2");
        fr.setResponseHeaders(headers);

        when(requestProcessor.process(any(), any())).thenReturn(fr);

        final ResponseEntity<?> respEntity = controller.handleAll(req, null, resp);

        // Content-Type preserved
        assertThat(respEntity.getHeaders().getContentType().toString()).isEqualTo("application/json");
        // our allowed custom header is forwarded
        assertThat(respEntity.getHeaders().getFirst("X-Ok")).isEqualTo("v2");
        // null-value header must not be present
        assertThat(respEntity.getHeaders().containsKey("X-Null-Value")).isFalse();
    }

    @Test
    @DisplayName("handleAll：当 servletResponse.setHeader 抛异常时被忽略并正常返回")
    void testHandleAll_servletResponseSetHeaderThrows_isIgnored() throws Exception {
        final HttpServletRequest req = mock(HttpServletRequest.class);
        final HttpServletResponse resp = mock(HttpServletResponse.class);

        when(req.getHeader("X-Request-Id")).thenReturn(null);
        when(req.getMethod()).thenReturn("POST");
        when(req.getRequestURI()).thenReturn("/unit3");
        when(logMessageService.format(any(), any())).thenReturn("msg");

        final ForwardResult fr = new ForwardResult();
        fr.setStatus(200);
        fr.setResponseBody(null);
        when(requestProcessor.process(any(), any())).thenReturn(fr);

        // make servletResponse.setHeader throw for any call
        doThrow(new RuntimeException("boom")).when(resp).setHeader(any(), any());

        final ResponseEntity<?> respEntity = controller.handleAll(req, null, resp);

        // still returns OK and includes generated X-Request-Id
        assertThat(respEntity.getStatusCodeValue()).isEqualTo(200);
        assertThat(respEntity.getHeaders().containsKey("X-Request-Id")).isTrue();
    }

    @Test
    @DisplayName("handleAll：当 UUID.randomUUID().toString 返回 null 时，invokeId 为 null，异常分支不写入 header")
    void testHandleAll_uuidToStringNull_exceptionBranch_noInvokeHeader() throws Exception {
        final HttpServletRequest req = mock(HttpServletRequest.class);
        final HttpServletResponse resp = mock(HttpServletResponse.class);

        when(req.getHeader("X-Request-Id")).thenReturn(null);
        when(req.getMethod()).thenReturn("POST");
        when(req.getRequestURI()).thenReturn("/unit-null-id");
        when(logMessageService.format(any(), any())).thenReturn("msg");

        // make UUID.randomUUID().toString() return null
        final UUID mockUuid = mock(UUID.class);
        when(mockUuid.toString()).thenReturn(null);
        try (MockedStatic<UUID> mocked = Mockito.mockStatic(UUID.class)) {
            mocked.when(UUID::randomUUID).thenReturn(mockUuid);

            final ForwardResult fr = new ForwardResult();
            fr.setExceptionOccurred(true);
            when(requestProcessor.process(any(), any())).thenReturn(fr);

            final ResponseEntity<?> respEntity = controller.handleAll(req, null, resp);
            // since invokeId resolved to null, handler must NOT set X-Request-Id header in 500 response
            assertThat(respEntity.getStatusCodeValue()).isEqualTo(500);
            assertThat(respEntity.getHeaders().containsKey("X-Request-Id")).isFalse();
        }
    }

    @Test
    @DisplayName("handleAll：当 UUID.randomUUID().toString 返回 null 时，最终响应也不包含 X-Request-Id")
    void testHandleAll_uuidToStringNull_noInvokeHeaderInResponse() throws Exception {
        final HttpServletRequest req = mock(HttpServletRequest.class);
        final HttpServletResponse resp = mock(HttpServletResponse.class);

        when(req.getHeader("X-Request-Id")).thenReturn(null);
        when(req.getMethod()).thenReturn("POST");
        when(req.getRequestURI()).thenReturn("/unit-null-id2");
        when(logMessageService.format(any(), any())).thenReturn("msg");

        final UUID mockUuid = mock(UUID.class);
        when(mockUuid.toString()).thenReturn(null);
        try (MockedStatic<UUID> mocked = Mockito.mockStatic(UUID.class)) {
            mocked.when(UUID::randomUUID).thenReturn(mockUuid);

            final ForwardResult fr = new ForwardResult();
            fr.setStatus(200);
            fr.setResponseBody("ok".getBytes());
            fr.setResponseHeaders(new LinkedHashMap<>());
            when(requestProcessor.process(any(), any())).thenReturn(fr);

            final ResponseEntity<?> respEntity = controller.handleAll(req, null, resp);
            assertThat(respEntity.getStatusCodeValue()).isEqualTo(200);
            // X-Request-Id should not be present because invokeId was null
            assertThat(respEntity.getHeaders().containsKey("X-Request-Id")).isFalse();
        }
    }

    @Test
    @DisplayName("handleAll：当 X-Request-Id 为空字符串时，视为未提供并生成 invokeId")
    void testHandleAll_emptyHeader_generatesInvokeId() throws Exception {
        final HttpServletRequest req = mock(HttpServletRequest.class);
        final HttpServletResponse resp = mock(HttpServletResponse.class);

        when(req.getHeader("X-Request-Id")).thenReturn("");
        when(req.getMethod()).thenReturn("POST");
        when(req.getRequestURI()).thenReturn("/unit-empty");
        when(logMessageService.format(any(), any())).thenReturn("msg");

        final ForwardResult fr = new ForwardResult();
        fr.setStatus(200);
        when(requestProcessor.process(any(), any())).thenReturn(fr);

        final ResponseEntity<?> respEntity = controller.handleAll(req, null, resp);
        assertThat(respEntity.getStatusCodeValue()).isEqualTo(200);
        // a generated invoke id should be set
        assertThat(respEntity.getHeaders().containsKey("X-Request-Id")).isTrue();
        assertThat(respEntity.getHeaders().getFirst("X-Request-Id")).isNotEmpty();
    }
}



