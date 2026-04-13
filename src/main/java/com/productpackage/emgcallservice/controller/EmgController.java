package com.productpackage.emgcallservice.controller;

import com.productpackage.emgcallservice.service.RequestProcessor;
import com.productpackage.emgcallservice.model.ForwardResult;
import com.productpackage.emgcallservice.exception.BusinessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.productpackage.emgcallservice.util.LogMessageService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.UUID;

/**
 * 通用接入 Controller：匹配所有 path，并为每次请求生成/透传 invoke_id。
 */
@RestController
public class EmgController {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmgController.class);

    private final RequestProcessor requestProcessor;
    private final LogMessageService logMessageService;

    public EmgController(final RequestProcessor requestProcessor, final LogMessageService logMessageService) {
        this.requestProcessor = requestProcessor;
        this.logMessageService = logMessageService;
    }

    @RequestMapping(value = "/**")
    public ResponseEntity<?> handleAll(final HttpServletRequest request,
                                       @RequestBody(required = false) final byte[] body,
                                       final HttpServletResponse servletResponse) throws Exception {
        // 透传或生成 invoke_id，并设置到 request attribute 以便下游日志/exception handler 使用
        String invokeId = request.getHeader("X-Request-Id");
        if (invokeId == null || invokeId.isEmpty()) {
            invokeId = UUID.randomUUID().toString();
        }
        request.setAttribute("invoke_id", invokeId);
        final String msg = logMessageService.format(
                "log.requestReceived",
                com.productpackage.emgcallservice.util.Constants.BUSINESS_CODE,
                request.getMethod(),
                request.getRequestURI(),
                invokeId
        );
        LOGGER.info(msg);

        // delegate to processor; exceptions will be handled by GlobalExceptionHandler
        final ForwardResult fr = requestProcessor.process(request, body);

        // 回写 invokeId 到响应 header
        // reuse existing invokeId set earlier

        if (fr == null) {
            // 不应发生：处理器未返回结果，视为内部错误
            throw new BusinessException("E-" + com.productpackage.emgcallservice.util.Constants.BUSINESS_CODE + "-0399",
                    "Processor returned null", 500);
        }

        if (fr.isExceptionOccurred()) {
            // 转发期间发生异常且无下游响应：返回 HTTP 500 空 body
            final HttpHeaders headers = new HttpHeaders();
            if (invokeId != null) {
                headers.add("X-Request-Id", invokeId);
            }
            return ResponseEntity.status(500).headers(headers).build();
        }

        // 有下游响应（可能为 2xx 或 非2xx），透传 status/body/headers
        final int status = fr.getStatus() <= 0 ? 200 : fr.getStatus();
        final HttpHeaders respHeaders = new HttpHeaders();
        if (fr.getResponseHeaders() != null) {
            fr.getResponseHeaders().forEach((k, v) -> {
                if (k == null) {
                    return;
                }
                final String keyLower = k.trim().toLowerCase(java.util.Locale.ROOT);
                // 过滤掉不应透传的响应头（hop-by-hop 等）
                if (com.productpackage.emgcallservice.util.Constants.RESPONSE_HEADERS_TO_EXCLUDE.contains(keyLower)) {
                    return;
                }
                // 过滤空值
                if (v == null) {
                    return;
                }
                respHeaders.add(k, v);
            });
        }
        if (invokeId != null) {
            respHeaders.set("X-Request-Id", invokeId);
        }

        final byte[] respBody = fr.getResponseBody();
        
        // 如果响应没有 Content-Type，设置为 application/octet-stream（或按设计调整）
        if (!respHeaders.containsKey(HttpHeaders.CONTENT_TYPE)) {
            respHeaders.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        }

        // 确保 hop-by-hop 头在最终 servlet 层不被透传（某些容器可能自动添加 Connection 等），再返回
        try {
            servletResponse.setHeader("Connection", null);
            servletResponse.setHeader("Keep-Alive", null);
            servletResponse.setHeader("Transfer-Encoding", null);
        } catch (final Exception ignore) {
            // 容器可能不支持将 header 设为 null；忽略并继续返回 ResponseEntity
        }

        return ResponseEntity.status(status).headers(respHeaders).body(respBody == null ? new byte[0] : respBody);
    }
}







