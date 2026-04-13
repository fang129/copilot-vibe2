package com.productpackage.emgcallservice.util;

/**
 * 常量集中定义（便于统一管理）。
 */
public final class Constants {

    private Constants() {
        // no instance
    }

    public static final String BUSINESS_CODE = "MCMOD_EMGCA";

    public static final String DEFAULT_CONTENT_TYPE_FORWARD = "application/x-www-form-urlencoded";
    public static final String RECEIVED_CONTENT_TYPE = "application/octet-stream";

    public static final int DEFAULT_TIMEOUT_MS = 60000;
    public static final int DEFAULT_RETRY = 0;
    public static final int DEFAULT_INTERVAL_MS = 1000;

    /**
     * 日志参数最大长度（Base64 串截断阈值）
     */
    public static final int LOG_PARAM_MAX_LENGTH = 4096;
    /**
     * 不应透传的响应头（hop-by-hop 等），统一以小写表示用于比较过滤
     */
    public static final java.util.Set<String> RESPONSE_HEADERS_TO_EXCLUDE = new java.util.HashSet<java.lang.String>(
            java.util.Arrays.asList(
                    "transfer-encoding",
                    "connection",
                    "keep-alive",
                    "proxy-authenticate",
                    "proxy-authorization",
                    "te",
                    "trailer",
                    "upgrade",
                    "content-length"
            )
    );
    /**
     * 默认行为定义文件名（可通过 system property behaviordefinition.path 覆盖）
     */
    public static final String DEFAULT_BEHAVIOR_FILE = "behaviordefinition.json";
}



