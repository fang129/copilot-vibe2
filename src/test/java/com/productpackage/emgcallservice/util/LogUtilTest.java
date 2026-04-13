package com.productpackage.emgcallservice.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class LogUtilTest {

    @Test
    @DisplayName("toBase64：null/空返回空字符串")
    public void testToBase64_nullOrEmpty_returnsEmptyString() {
        assertThat(LogUtil.toBase64((byte[]) null)).isEqualTo("");
        assertThat(LogUtil.toBase64(new byte[0])).isEqualTo("");
        assertThat(LogUtil.toBase64((String) null)).isEqualTo("");
        assertThat(LogUtil.toBase64("")).isEqualTo("");
    }

    @Test
    @DisplayName("toBase64：字符串与字节数组正确编码")
    public void testToBase64_stringAndBytes_encodesCorrectly() {
        byte[] bytes = "abc".getBytes(StandardCharsets.UTF_8);
        String expected = java.util.Base64.getEncoder().encodeToString(bytes);
        assertThat(LogUtil.toBase64(bytes)).isEqualTo(expected);
        assertThat(LogUtil.toBase64("abc")).isEqualTo(expected);
    }

    @Test
    @DisplayName("headerMapToString：敏感字段脱敏，保留前三字符其余为 '*'")
    public void testHeaderMapToString_sensitiveMasking_appliesMask() {
        Map<String, String> map = new HashMap<>();
        map.put("u_tel", "13800138000");
        map.put("other", "value");
        String out = LogUtil.headerMapToString(map);
        assertThat(out).contains("u_tel:138********");
        assertThat(out).contains("other:value");
    }

    @Test
    @DisplayName("headerMapToString：不可打印字符被移除以防日志注入")
    public void testHeaderMapToString_nonPrintable_removed() {
        Map<String, String> map = new HashMap<>();
        map.put("k", "abc\u0000def");
        String out = LogUtil.headerMapToString(map);
        assertThat(out).doesNotContain("\u0000");
        assertThat(out).contains("k:abcdef");
    }

    @Test
    @DisplayName("truncate：null 返回空，短串原样返回，超长截断并追加 ...(truncated)")
    public void testTruncate_nullOrShortOrLong() {
        assertThat(LogUtil.truncate((String) null)).isEqualTo("");
        assertThat(LogUtil.truncate("short")).isEqualTo("short");
        int max = 10;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < max + 5; i++) {
            sb.append('a');
        }
        String longStr = sb.toString();
        String t = LogUtil.truncate(longStr, max);
        assertThat(t).contains("...(truncated)");
        assertThat(t.length()).isGreaterThan(max);
    }
}
