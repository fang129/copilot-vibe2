package com.productpackage.emgcallservice.util;

import org.springframework.stereotype.Component;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * URL-encoded 解析器，保留字段顺序与重复字段。
 * 提供 coordinate 无效化的工具方法。
 */
@Component
public class UrlEncodedParser {

    /**
     * 保序键值对。
     */
    public static final class Pair {
        public final String key;
        public final String value;

        public Pair(final String key, final String value) {
            this.key = key;
            this.value = value;
        }
    }

    private static final Pattern P_LAT_PATTERN = Pattern.compile("^P_LAT(\\d+)$");
    private static final Pattern P_LON_PATTERN = Pattern.compile("^P_LON(\\d+)$");
    private static final Pattern P_TYP_PATTERN = Pattern.compile("^P_TYP$");

    /**
     * 简单判断字符串是否可能为 urlencoded。
     */
    public boolean isValidUrlEncoded(final String s) {
        if (s == null) {
            return false;
        }
        return s.contains("=") || s.contains("&");
    }

    /**
     * 解析为按顺序的键值对（支持重复 key，支持空 value）。
     */
    public List<Pair> parseToOrderedList(final String s) {
        final List<Pair> out = new ArrayList<>();
        if (s == null) {
            return out;
        }
        if (s.isEmpty()) {
            return out;
        }
        final String[] parts = s.split("&", -1);
        for (final String p : parts) {
            final int idx = p.indexOf('=');
            String k;
            String v;
            if (idx >= 0) {
                k = decode(p.substring(0, idx));
                v = decode(p.substring(idx + 1));
            } else {
                k = decode(p);
                v = "";
            }
            out.add(new Pair(k, v));
        }
        return out;
    }

    private String decode(final String s) {
        return decodeWithCharset(s, "UTF-8");
    }

    private String encode(final String s) {
        return encodeWithCharset(s, "UTF-8");
    }

    /* package-private for testing: allow supplying charset to trigger fallback branches */
    String decodeWithCharset(final String s, final String charset) {
        try {
            return URLDecoder.decode(s, charset);
        } catch (final UnsupportedEncodingException e) {
            return s;
        }
    }

    /* package-private for testing: allow supplying charset to trigger fallback branches */
    String encodeWithCharset(final String s, final String charset) {
        try {
            return URLEncoder.encode(s == null ? "" : s, charset);
        } catch (final UnsupportedEncodingException e) {
            return s;
        }
    }

    /**
     * 将键值对序列化回 urlencoded 字符串，保持顺序与重复字段。
     */
    public String serialize(final List<Pair> entries) {
        final StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (final Pair p : entries) {
            if (!first) {
                sb.append("&");
            }
            first = false;
            sb.append(encode(p.key));
            sb.append("=");
            sb.append(encode(p.value));
        }
        return sb.toString();
    }

    /**
     * 按业务规则对坐标进行无效化替换。
     *
     * 规则（实现说明）：
     * - 查找第一个 P_TYP（如果存在并可解析为 int），判断模式：
     *   - 若 (pTypParsed & 0x1) != 0 则视为 hex 模式（使用 hex 无效常量）
     *   - 否则视为 decimal 模式（使用 dec 无效常量）
     * - 若 P_TYP 不存在或解析异常，视为非法：按文档要求对全部组替换为无效坐标（使用 decimal 无效常量）
     * - 对所有 key 匹配 P_LATn / P_LONn 的字段，无论原值是否已经是无效值，都替换为对应无效常量（保持 P_NO 不变）
     * - 支持序号不连续、只有经度或只有纬度的情况（单独替换存在的字段）
     *
     * @param entries 原始按序键值对（会返回新的 list 对象，原 entries 不修改）
     * @param invalidHex 无效 hex 值（例如 "FFFFFFFF"）
     * @param invalidDec 无效 dec 值（例如 "999.9999999"）
     * @return 替换后的新 entries（顺序与重复保持）
     */
    public List<Pair> applyCoordinateRules(final List<Pair> entries,
                                          final String invalidHex,
                                          final String invalidDec) {
        if (entries == null || entries.isEmpty()) {
            return entries;
        }

        // 先查找 P_TYP 并尝试解析
        Integer pTypParsed = null;
        for (final Pair p : entries) {
            if (pTypMatch(p.key)) {
                try {
                    pTypParsed = Integer.parseInt(p.value);
                } catch (final Exception e) {
                    pTypParsed = null;
                }
                break;
            }
        }

        final boolean pTypIllegal = (pTypParsed == null);
        final boolean isHexMode = (!pTypIllegal) && ((pTypParsed & 0x1) != 0);

        final String invalidValue = pTypIllegal ? invalidDec : (isHexMode ? invalidHex : invalidDec);

        final List<Pair> out = new ArrayList<>(entries.size());
        for (final Pair p : entries) {
            // 如果是 P_LATn 或 P_LONn，替换为 invalidValue
            if (isLat(p.key) || isLon(p.key)) {
                String orig = p.value == null ? "" : p.value;
                String prefix = "";
                if (!orig.isEmpty() && Character.isLetter(orig.charAt(0))) {
                    prefix = orig.substring(0, 1); // keep one-letter direction prefix like 'N' or 'E'
                }
                out.add(new Pair(p.key, prefix + invalidValue));
            } else {
                out.add(new Pair(p.key, p.value));
            }
        }
        return out;
    }

    private boolean pTypMatch(final String key) {
        return key != null && P_TYP_PATTERN.matcher(key).matches();
    }

    private boolean isLat(final String key) {
        if (key == null) {
            return false;
        }
        final Matcher m = P_LAT_PATTERN.matcher(key);
        return m.matches();
    }

    private boolean isLon(final String key) {
        if (key == null) {
            return false;
        }
        final Matcher m = P_LON_PATTERN.matcher(key);
        return m.matches();
    }
}

