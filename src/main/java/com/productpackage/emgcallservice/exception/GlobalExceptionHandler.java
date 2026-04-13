package com.productpackage.emgcallservice.exception;

import com.productpackage.emgcallservice.util.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.productpackage.emgcallservice.util.LogMessageService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.servlet.http.HttpServletRequest;

/**
 * 全局异常处理：统一记录日志并返回适当 HTTP 响应（按设计：某些错误返回 500 空 body）。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final LogMessageService logMessageService;

    public GlobalExceptionHandler(final LogMessageService logMessageService) {
        this.logMessageService = logMessageService;
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<?> handleBusiness(final BusinessException ex, final HttpServletRequest req) {
        final String invokeId = (String) req.getAttribute("invoke_id");
        final String msg = logMessageService.format(
                "log.exception.business",
                Constants.BUSINESS_CODE,
                ex.getErrorCode(),
                ex.getMessage(),
                invokeId,
                req.getRequestURI()
        );
        LOGGER.error(msg, ex);
        // 按设计：对于最终应返回 500 的情况，返回空 body
        if (ex.getHttpStatus() == 500) {
            return ResponseEntity.status(500).build();
        }
        final ErrorResponse resp = new ErrorResponse(ex.getErrorCode(), ex.getMessage(), invokeId);
        return ResponseEntity.status(ex.getHttpStatus()).body(resp);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleOther(final Exception ex, final HttpServletRequest req) {
        final String invokeId = (String) req.getAttribute("invoke_id");
        final String msg = logMessageService.format(
                "log.exception.unknown",
                Constants.BUSINESS_CODE,
                invokeId,
                req.getRequestURI(),
                ex.getMessage()
        );
        LOGGER.error(msg, ex);
        // 按设计：未捕获异常返回 HTTP 500 空 body
        return ResponseEntity.status(500).build();
    }
}


