package com.productpackage.emgcallservice.util;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

/**
 * 日志工具：Base64 编码、headers 序列化、敏感字段脱敏、截断等通用操作。
 */
public final class LogUtil {

    // 默认记录参数的最大长度（Base64 字符串长度后再截断）
    public static final int DEFAULT_MAX_LOG_PARAM_LENGTH = 4096;

    // 常见敏感 header（小写）
    private static final Set<String> SENSITIVE_HEADERS = new HashSet<>(Arrays.asList(
            "u_id", "u_tel", "authorization", "proxy-authorization", "cookie"
    ));

    // 简单电码：用于把 header key 规范成小写比较
    private static final Pattern NON_PRINTABLE = Pattern.compile("\\p{C}");

    private LogUtil() {
        // no instance
    }

    public static String toBase64(final byte[] data) {
        if (data == null || data.length == 0) {
            return "";
        }
        return Base64.getEncoder().encodeToString(data);
    }

    public static String toBase64(final String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 将 headerMap 序列化为 key:value;key2:value2，并对敏感字段做脱敏处理（部分保留）。
     *
     * @param headerMap headers 映射（key -> comma separated values）
     * @return 序列化后字符串
     */
    public static String headerMapToString(final Map<String, String> headerMap) {
        if (headerMap == null || headerMap.isEmpty()) {
            return "";
        }
        final StringBuilder sb = new StringBuilder();
        headerMap.forEach((k, v) -> {
            if (sb.length() > 0) {
                sb.append(";");
            }
            final String normKey = k == null ? "" : k.trim();
            final String outVal = maskIfSensitive(normKey, v == null ? "" : v);
            sb.append(normKey).append(":").append(outVal);
        });
        return sb.toString();
    }

    private static String maskIfSensitive(final String key, final String value) {
        if (key == null) {
            return value;
        }
        final String lower = key.trim().toLowerCase(Locale.ROOT);
        if (SENSITIVE_HEADERS.contains(lower)) {
            // 保留前 3 个字符，其他用 '*'
            final int keep = Math.min(3, value.length());
            final StringBuilder sb = new StringBuilder();
            sb.append(value, 0, keep);
            for (int i = keep; i < value.length(); i++) {
                sb.append('*');
            }
            return sb.toString();
        }
        // 移除不可打印字符，避免日志注入
        return NON_PRINTABLE.matcher(value).replaceAll("");
    }

    /**
     * 截断字符串到指定长度，超出部分标注截断信息。
     *
     * @param s      输入字符串
     * @param maxLen 最大长度
     * @return 截断结果
     */
    public static String truncate(final String s, final int maxLen) {
        if (s == null) {
            return "";
        }
        if (s.length() <= maxLen) {
            return s;
        }
        return s.substring(0, maxLen) + "...(truncated)";
    }

    public static String truncate(final String s) {
        return truncate(s, DEFAULT_MAX_LOG_PARAM_LENGTH);
    }
}

