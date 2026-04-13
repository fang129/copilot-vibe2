package com.productpackage.emgcallservice.exception;

import com.productpackage.emgcallservice.util.Constants;
import com.productpackage.emgcallservice.util.LogMessageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class GlobalExceptionHandlerTest {

    @Mock
    private LogMessageService logMessageService;

    @InjectMocks
    private GlobalExceptionHandler handler;

    @Test
    @DisplayName("handleBusiness：当 httpStatus 为 500 时返回 500 空 body")
    public void testHandleBusiness_status500_returnsEmptyBody() {
        final MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI("/test");
        req.setAttribute("invoke_id", "INVOKE1");

        final BusinessException ex = new BusinessException("EC", "err", 500);
        final ResponseEntity<?> resp = handler.handleBusiness(ex, req);
        assertThat(resp.getStatusCodeValue()).isEqualTo(500);
        assertThat(resp.getBody()).isNull();

        verify(logMessageService, times(1)).format(eq("log.exception.business"), eq(Constants.BUSINESS_CODE), eq("EC"), eq("err"), eq("INVOKE1"), eq("/test"));
    }

    @Test
    @DisplayName("handleBusiness：非 500 状态返回 ErrorResponse body 且包含 errorCode/message/invokeId")
    public void testHandleBusiness_non500_returnsErrorResponseBody() {
        final MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI("/zzz");
        req.setAttribute("invoke_id", "IID");

        final BusinessException ex = new BusinessException("EC2", "bad", 400);

        final ResponseEntity<?> resp = handler.handleBusiness(ex, req);
        assertThat(resp.getStatusCodeValue()).isEqualTo(400);
        assertThat(resp.getBody()).isInstanceOf(ErrorResponse.class);

        final ErrorResponse body = (ErrorResponse) resp.getBody();
        assertThat(body.getErrorCode()).isEqualTo("EC2");
        assertThat(body.getMessage()).isEqualTo("bad");
        assertThat(body.getInvokeId()).isEqualTo("IID");

        verify(logMessageService, times(1)).format(eq("log.exception.business"), eq(Constants.BUSINESS_CODE), eq("EC2"), eq("bad"), eq("IID"), eq("/zzz"));
    }

    @Test
    @DisplayName("handleOther：对于任意 Exception 返回 500 空 body 且记录日志")
    public void testHandleOther_returns500_emptyBody_and_logs() {
        final MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI("/u");
        req.setAttribute("invoke_id", "ID2");

        final Exception ex = new RuntimeException("boom");

        final ResponseEntity<?> resp = handler.handleOther(ex, req);
        assertThat(resp.getStatusCodeValue()).isEqualTo(500);
        assertThat(resp.getBody()).isNull();

        verify(logMessageService, times(1)).format(eq("log.exception.unknown"), eq(Constants.BUSINESS_CODE), eq("ID2"), eq("/u"), eq("boom"));
    }
}


