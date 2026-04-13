package com.productpackage.emgcallservice.controller;

import com.productpackage.emgcallservice.exception.GlobalExceptionHandler;
import com.productpackage.emgcallservice.model.ForwardResult;
import com.productpackage.emgcallservice.service.RequestProcessor;
import com.productpackage.emgcallservice.util.LogMessageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@WebMvcTest(controllers = EmgController.class)
@Import(GlobalExceptionHandler.class)
public class EmgControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RequestProcessor requestProcessor;

    @MockBean
    private LogMessageService logMessageService;

    @Test
    @DisplayName("handleAll：无 X-Request-Id 时生成 invoke_id 并回写到响应 header")
    public void testHandleAll_noInvokeId_generatedAndSetHeader() throws Exception {
        ForwardResult fr = new ForwardResult();
        fr.setStatus(201);
        fr.setResponseBody("resp".getBytes());
        Map<String,String> rh = new HashMap<>(); rh.put("X-Other","v");
        fr.setResponseHeaders(rh);
        when(requestProcessor.process(any(), any())).thenReturn(fr);

        final String url = "/any/path";
        mockMvc.perform(post(url).content("bodydata").contentType(MediaType.APPLICATION_OCTET_STREAM))
                .andExpect(status().isCreated())
                .andExpect(header().exists("X-Request-Id"));
    }

    @Test
    @DisplayName("handleAll：processor 返回 null 时抛 BusinessException 并由 GlobalExceptionHandler 处理为 500")
    public void testHandleAll_processorReturnsNull_throwsBusinessException() throws Exception {
        when(requestProcessor.process(any(), any())).thenReturn(null);
        final String url = "/p";
        mockMvc.perform(post(url).content(new byte[0]).contentType(MediaType.APPLICATION_OCTET_STREAM))
                .andExpect(status().isInternalServerError());
    }

    @Test
    @DisplayName("handleAll：forward 发生异常时返回 500 空 body 并包含 X-Request-Id")
    public void testHandleAll_forwardException_returns500_emptyBody_withInvokeId() throws Exception {
        ForwardResult fr = new ForwardResult();
        fr.setExceptionOccurred(true);
        when(requestProcessor.process(any(), any())).thenReturn(fr);

        final String url = "/p2";
        mockMvc.perform(post(url).content("b").contentType(MediaType.APPLICATION_OCTET_STREAM))
                .andExpect(status().isInternalServerError())
                .andExpect(header().exists("X-Request-Id"));
    }

    @Test
    @DisplayName("handleAll：透传下游响应头并过滤 hop-by-hop 头")
    public void testHandleAll_responseHeaders_filteredAndBodyReturned() throws Exception {
        ForwardResult fr = new ForwardResult();
        fr.setStatus(200);
        fr.setResponseBody("ok".getBytes());
        Map<String,String> rh = new HashMap<>();
        rh.put("Connection","keep-alive");
        rh.put("X-Allowed","v1");
        fr.setResponseHeaders(rh);
        when(requestProcessor.process(any(), any())).thenReturn(fr);

        mockMvc.perform(post("/x").content("b").contentType(MediaType.APPLICATION_OCTET_STREAM))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Allowed", "v1"));
        // ensure Connection not forwarded
        // header().doesNotExist not available, instead fetch and assert via mvc result:
        String conn = mockMvc.perform(post("/x").content("b").contentType(MediaType.APPLICATION_OCTET_STREAM))
                .andReturn().getResponse().getHeader("Connection");
        assertThat(conn).isNull();
    }
}

