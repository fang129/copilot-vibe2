package com.productpackage.emgcallservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.productpackage.emgcallservice.exception.BusinessException;
import com.productpackage.emgcallservice.model.ForwardResult;
import com.productpackage.emgcallservice.model.ForwardedRequestData;
import com.productpackage.emgcallservice.util.Constants;
import com.productpackage.emgcallservice.util.LogMessageService;
import com.productpackage.emgcallservice.util.LogUtil;
import com.productpackage.emgcallservice.util.UrlEncodedParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 处理入口：负责根据 Behavior 调度 B/D 逻辑、形成 ForwardedRequestData 并调用 ForwarderService 转发。
 *
 * 已将原 process 中的子职责拆分为多个 public 方法，便于测试与复用：
 * - findBehaviorForPath
 * - createForwardedRequest
 * - transformBodyByPattern
 * - forwardRequest
 */
@Service
public class RequestProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(RequestProcessor.class);

    private final BehaviorLoader behaviorLoader;
    private final UrlEncodedParser urlEncodedParser;
    private final ForwarderService forwarderService;
    private final LogMessageService logMessageService;

    // invalid constants 可注入，当前使用 system properties 或默认
    private final String invalidHex = System.getProperty("emg.invalid.hex", "FFFFFFFF");
    private final String invalidDec = System.getProperty("emg.invalid.dec", "999.9999999");

    public RequestProcessor(final BehaviorLoader behaviorLoader,
                            final UrlEncodedParser urlEncodedParser,
                            final ForwarderService forwarderService,
                            final LogMessageService logMessageService) {
        this.behaviorLoader = behaviorLoader;
        this.urlEncodedParser = urlEncodedParser;
        this.forwarderService = forwarderService;
        this.logMessageService = logMessageService;
    }

    /**
     * 处理请求并执行转发（同步阻塞）。
     *
     * 高层编排：查找行为 -> 构建 ForwardedRequestData -> 转发 -> 处理转发异常/返回
     */
    public ForwardResult process(final HttpServletRequest request, final byte[] rawBody) {
        final String path = request.getRequestURI();
        final JsonNode behavior = findBehaviorForPath(path, rawBody);

        final String rewriteUrl = behavior.path("RewriteUrl").asText(null);
        if (rewriteUrl == null || rewriteUrl.isEmpty()) {
            final String paramBase64 = LogUtil.toBase64(rawBody);
            final String err = logMessageService.format("log.configError",
                    Constants.BUSINESS_CODE, "RewriteUrl missing for PatternURL=" + path, paramBase64);
            LOGGER.error(err);
            throw new BusinessException("E-" + Constants.BUSINESS_CODE + "-0001", "Missing rewriteUrl", 500);
        }

        final int timeoutMs = parseIntSafe(behavior.path("Timeout").asText(null), Constants.DEFAULT_TIMEOUT_MS);
        final int retryCount = parseIntSafe(behavior.path("RetryCount").asText(null), Constants.DEFAULT_RETRY);
        final int intervalMs = parseIntSafe(behavior.path("IntervalBetweenRetries").asText(null), Constants.DEFAULT_INTERVAL_MS);
        final String downstreamName = "ECDP"; // 可在 JSON 中配置

        final ForwardedRequestData forward = createForwardedRequest(request, rawBody, behavior);

        final ForwardResult fr = forwardRequest(downstreamName, rewriteUrl, forward, timeoutMs, retryCount, intervalMs);

        if (fr.isExceptionOccurred()) {
            final String paramBase64 = LogUtil.toBase64(forward.getBodyBin());
            final String headerStr = LogUtil.headerMapToString(forward.getHeaderMap());
            final String err = logMessageService.format("log.apiCallRetryExceeded",
                    Constants.BUSINESS_CODE, downstreamName, rewriteUrl, paramBase64, headerStr, "retry exceeded");
            LOGGER.error(err);
            return fr;
        }

        return fr;
    }

    /**
     * 查找并返回行为配置，未找到时记录日志并抛 BusinessException（保持原行为）。
     */
    public JsonNode findBehaviorForPath(final String path, final byte[] rawBody) {
        final Optional<JsonNode> behaviorOpt = behaviorLoader.findBehaviorForPath(path);
        if (!behaviorOpt.isPresent()) {
            final String paramBase64 = LogUtil.toBase64(rawBody);
            final String err = logMessageService.format("log.configError",
                    Constants.BUSINESS_CODE, "PatternURL=" + path, paramBase64);
            LOGGER.error(err);
            throw new BusinessException("E-" + Constants.BUSINESS_CODE + "-0001", "Behavior config error", 500);
        }
        return behaviorOpt.get();
    }

    /**
     * 根据 HttpServletRequest 与行为构造 ForwardedRequestData（包含 header/query/uri 与 body 转换）。
     */
    public ForwardedRequestData createForwardedRequest(final HttpServletRequest request,
                                                       final byte[] rawBody,
                                                       final JsonNode behavior) {
        final String pattern = behavior.path("Pattern").asText("D");
        final ForwardedRequestData forward = new ForwardedRequestData();

        // headers
        final Map<String, String> headers = new HashMap<>();
        final Enumeration<String> headerNames = request.getHeaderNames();
        if (headerNames != null) {
            while (headerNames.hasMoreElements()) {
                final String hn = headerNames.nextElement();
                final Enumeration<String> hv = request.getHeaders(hn);
                final List<String> values = new ArrayList<>();
                while (hv.hasMoreElements()) {
                    values.add(hv.nextElement());
                }
                headers.put(hn, String.join(",", values));
            }
        }
        // ensure forwarded content-type is the default form content type
        headers.put("Content-Type", Constants.DEFAULT_CONTENT_TYPE_FORWARD);
        forward.setHeaderMap(headers);

        forward.setQueryString(request.getQueryString());
        forward.setRequestUri(request.getRequestURI());

        // body 处理委托给 transformBodyByPattern
        final byte[] transformed = transformBodyByPattern(pattern, rawBody);
        forward.setBodyBin(transformed);

        return forward;
    }

    /**
     * 将 pattern 判定与坐标变换逻辑提取为独立方法。
     * - 当 pattern 为 "D" 时直接透传 body 二进制
     * - 否则尝试解析为 URL encoded，应用 coordinate rules 并序列化回 bytes；解析失败时透传原始二进制
     */
    public byte[] transformBodyByPattern(final String pattern, final byte[] rawBody) {
        if ("D".equalsIgnoreCase(pattern)) {
            return rawBody;
        }

        if (rawBody == null || rawBody.length == 0) {
            return rawBody;
        }

        final String decoded = new String(rawBody, StandardCharsets.UTF_8);
        if (urlEncodedParser.isValidUrlEncoded(decoded)) {
            final List<UrlEncodedParser.Pair> entries = urlEncodedParser.parseToOrderedList(decoded);
            if (!entries.isEmpty()) {
                final List<UrlEncodedParser.Pair> replaced = urlEncodedParser.applyCoordinateRules(entries, invalidHex, invalidDec);
                final String serialized = urlEncodedParser.serialize(replaced);
                return serialized.getBytes(StandardCharsets.UTF_8);
            }
        }

        // 解析失败或非 urlencoded：按设计直接透传原始二进制
        return rawBody;
    }

    /**
     * 将转发调用抽出，便于单独 mock 或测试。
     */
    public ForwardResult forwardRequest(final String downstreamName,
                                        final String rewriteUrl,
                                        final ForwardedRequestData forward,
                                        final int timeoutMs,
                                        final int retryCount,
                                        final int intervalMs) {
        return forwarderService.forward(downstreamName, rewriteUrl, forward, timeoutMs, retryCount, intervalMs);
    }

    private int parseIntSafe(final String s, final int def) {
        if (s == null) {
            return def;
        }
        try {
            return Integer.parseInt(s);
        } catch (final Exception e) {
            return def;
        }
    }
}



