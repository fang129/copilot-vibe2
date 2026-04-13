package com.productpackage.emgcallservice.service;

import com.productpackage.emgcallservice.model.ForwardResult;
import com.productpackage.emgcallservice.model.ForwardedRequestData;
import com.productpackage.emgcallservice.util.Constants;
import com.productpackage.emgcallservice.util.LogMessageService;
import com.productpackage.emgcallservice.util.LogUtil;
import org.apache.http.HttpEntity;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.Map;

/**
 * Forwarder：执行实际的 HTTP 转发，支持 per-try timeout、重试、日志记录（按模板）。
 */
@Service
public class ForwarderService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ForwarderService.class);

    private final LogMessageService logMessageService;

    public ForwarderService(final LogMessageService logMessageService) {
        this.logMessageService = logMessageService;
    }

    /**
     * 转发请求到下游（同步阻塞）。
     *
     * @param downstreamName 下游系统名称（用于日志）
     * @param rewriteUrl     目标地址
     * @param data           转发数据
     * @param timeoutMs      per-try timeout（包含 connect + socket）
     * @param retryCount     重试次数（0 表示不重试）
     * @param intervalMs     重试间隔 ms
     * @return ForwardResult 最终转发结果
     */
    public ForwardResult forward(final String downstreamName,
                                 final String rewriteUrl,
                                 final ForwardedRequestData data,
                                 final int timeoutMs,
                                 final int retryCount,
                                 final int intervalMs) {
        final ForwardResult result = new ForwardResult();
        result.setDownstreamName(downstreamName);

        final RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(timeoutMs)
                .setSocketTimeout(timeoutMs)
                .build();

        final byte[] bodyBytes = data.getBodyBin();
        final byte[] bodyForLogging = bodyBytes != null ? bodyBytes : serializeBodyMapForLogging(data.getBodyMap()).getBytes();
        final String paramBase64Full = LogUtil.toBase64(bodyForLogging);
        final String paramBase64 = LogUtil.truncate(paramBase64Full, Constants.LOG_PARAM_MAX_LENGTH);
        final String headerStr = LogUtil.headerMapToString(data.getHeaderMap());

        int attempts = 0;
        String lastErrorMessage = null;
        boolean gotResponse = false;

        while (attempts <= retryCount) {
            attempts++;
            result.setAttempts(attempts);
            try (CloseableHttpClient client = HttpClients.custom().setDefaultRequestConfig(requestConfig).build()) {
                // 使用 POST（如需透传 Method，可扩展）
                final HttpPost post = new HttpPost(rewriteUrl);
                post.setHeader("Content-Type", Constants.DEFAULT_CONTENT_TYPE_FORWARD);
                // 设置 headers（已在 RequestProcessor 做删除规则）
                    if (data.getHeaderMap() != null) {
                        for (final java.util.Map.Entry<String, String> e : data.getHeaderMap().entrySet()) {
                            final String key = e.getKey();
                            if (key == null) {
                                continue;
                            }
                            final String keyLower = key.trim().toLowerCase(java.util.Locale.ROOT);
                            // 不要转发 hop-by-hop 或者 Content-Length/Host 头
                            if (com.productpackage.emgcallservice.util.Constants.RESPONSE_HEADERS_TO_EXCLUDE.contains(keyLower)
                                    || "host".equals(keyLower)
                                    || "content-type".equals(keyLower)) {
                                continue;
                            }
                            final String value = e.getValue();
                            if (value != null) {
                                post.setHeader(key, value);
                            }
                        }
                    }

                final HttpEntity entity = (bodyBytes != null)
                        ? new ByteArrayEntity(bodyBytes, ContentType.create(Constants.DEFAULT_CONTENT_TYPE_FORWARD, "UTF-8"))
                        : new ByteArrayEntity(serializeBodyMap(data.getBodyMap()).getBytes("UTF-8"),
                        ContentType.create(Constants.DEFAULT_CONTENT_TYPE_FORWARD, "UTF-8"));

                post.setEntity(entity);

                try (CloseableHttpResponse response = client.execute(post)) {
                    final int status = response.getStatusLine().getStatusCode();
                    final byte[] respBody = response.getEntity() != null ? EntityUtils.toByteArray(response.getEntity()) : new byte[0];
                    final Map<String, String> respHeaders = new HashMap<>();
                    // 收集响应头（简化）
                    for (org.apache.http.Header h : response.getAllHeaders()) {
                        final String hn = h.getName();
                        if (hn == null) {
                            continue;
                        }
                        final String hnLower = hn.trim().toLowerCase(java.util.Locale.ROOT);
                        // 不应透传的响应头（hop-by-hop 等）在此处过滤
                        if (com.productpackage.emgcallservice.util.Constants.RESPONSE_HEADERS_TO_EXCLUDE.contains(hnLower)) {
                            continue;
                        }
                        respHeaders.put(hn, h.getValue());
                    }

                    result.setStatus(status);
                    result.setResponseBody(respBody);
                    result.setResponseHeaders(respHeaders);
                    result.setExceptionOccurred(false);
                    result.setTimeoutOccurred(false);
                    gotResponse = true;

                    if (status >= 200 && status < 300) {
                        // 成功：记录 info
                        final String info = logMessageService.format("log.processSuccess",
                                Constants.BUSINESS_CODE, status, rewriteUrl);
                        LOGGER.info(info);
                        return result;
                    }

                    // 非2xx: 如果还有重试次数，继续；否则记录 E-0301 并返回
                    if (attempts > retryCount) {
                        final String respBase64Full = LogUtil.toBase64(respBody);
                        final String respBase64 = LogUtil.truncate(respBase64Full, Constants.LOG_PARAM_MAX_LENGTH);
                        final String errMsg = logMessageService.format("log.apiCallFailedResponseNon200",
                                Constants.BUSINESS_CODE, downstreamName, rewriteUrl, paramBase64, headerStr, respBase64);
                        LOGGER.error(errMsg);
                        return result;
                    }
                    // 否则继续下一次尝试
                }
                } catch (final Exception ex) {
                lastErrorMessage = ex.getMessage();
                // 区分超时
                if (ex instanceof SocketTimeoutException) {
                    // 记录一次超时警告（W-0302）
                    result.setTimeoutOccurred(true);
                    final String warn = logMessageService.format("log.apiCallFailedResponseTimeout",
                            Constants.BUSINESS_CODE, downstreamName, rewriteUrl, paramBase64, headerStr, ex.getMessage());
                    LOGGER.warn(warn, ex);
                } else {
                    final String warn = logMessageService.format("log.apiCallUnexpectedError",
                            Constants.BUSINESS_CODE, downstreamName, rewriteUrl, paramBase64, headerStr, ex.getMessage());
                    LOGGER.warn(warn, ex);
                }

                // 若超过重试次数，记录 E-0303 或 E-0399 并返回 exceptionOccurred=true
                if (attempts > retryCount) {
                    // 超过最大尝试数
                    final String exceeded = logMessageService.format("log.apiCallRetryExceeded",
                            Constants.BUSINESS_CODE, downstreamName, rewriteUrl, paramBase64, headerStr, ex.getMessage());
                    LOGGER.error(exceeded, ex);
                    result.setExceptionOccurred(true);
                    result.setStatus(-1);
                    return result;
                }

                // 休眠后重试
                try {
                    Thread.sleep(intervalMs);
                } catch (final InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    final String err = logMessageService.format("log.apiCallUnexpectedError",
                            Constants.BUSINESS_CODE, downstreamName, rewriteUrl, paramBase64, headerStr, ie.getMessage());
                    LOGGER.error(err, ie);
                    result.setExceptionOccurred(true);
                    result.setStatus(-1);
                    return result;
                }
            }
        }

        // 最终仍未拿到响应
        if (!gotResponse) {
            final String exceeded = logMessageService.format("log.apiCallRetryExceeded",
                    Constants.BUSINESS_CODE, downstreamName, rewriteUrl, paramBase64, headerStr, lastErrorMessage);
            LOGGER.error(exceeded);
            result.setExceptionOccurred(true);
            result.setStatus(-1);
        }

        return result;
    }

    private static String serializeBodyMapForLogging(final org.springframework.util.MultiValueMap<String, String> map) {
        if (map == null || map.isEmpty()) {
            return "";
        }
        final StringBuilder sb = new StringBuilder();
        map.forEach((k, vs) -> {
            for (final String v : vs) {
                if (sb.length() > 0) {
                    sb.append("&");
                }
                sb.append(k).append("=").append(v == null ? "" : v);
            }
        });
        return sb.toString();
    }

    private static String serializeBodyMap(final org.springframework.util.MultiValueMap<String, String> map) {
        return serializeBodyMapForLogging(map);
    }
}




