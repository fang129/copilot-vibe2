package com.productpackage.emgcallservice.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.lang.reflect.Method;
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

    @Test
    @DisplayName("headerMapToString：组合场景，敏感字段、空 key、不可打印字符、trim 和多条目分隔")
    public void testHeaderMapToString_combinedScenarios() {
        Map<String, String> m = new HashMap<>();
        m.put("  authorization  ", "123456789");
        m.put("X-Header", "a\u0000b");
        m.put(null, "valueForNullKey");
        m.put("u_tel", "ab");

        String out = LogUtil.headerMapToString(m);

        assertThat(out).contains("authorization:123******");
        assertThat(out).contains("X-Header:ab");
        assertThat(out).contains(":valueForNullKey");
        assertThat(out).contains("u_tel:ab");
        assertThat(out).contains(";");
    }

    @Test
    @DisplayName("maskIfSensitive：key == null branch via reflection returns value unchanged")
    public void testMaskIfSensitive_keyNull_viaReflection_returnsValueUnchanged() throws Exception {
        Method m = LogUtil.class.getDeclaredMethod("maskIfSensitive", String.class, String.class);
        m.setAccessible(true);
        // invoke with explicit Object[] to ensure the first parameter (key) is truly null
        Object ret = m.invoke(null, new Object[]{null, "secretValue"});
        assertThat(ret).isEqualTo("secretValue");

        // also assert that when value is null the method returns null (direct return)
        Object retNull = m.invoke(null, new Object[]{null, null});
        assertThat(retNull).isNull();
    }

    @Test
    @DisplayName("maskIfSensitive：sensitive empty and exact-3-length and non-sensitive behaviour")
    public void testMaskIfSensitive_sensitiveEmpty_exact3_andNonSensitive() throws Exception {
        Method m = LogUtil.class.getDeclaredMethod("maskIfSensitive", String.class, String.class);
        m.setAccessible(true);

        // sensitive key with empty value -> returns empty string
        Object r1 = m.invoke(null, "u_tel", "");
        assertThat(r1).isEqualTo("");

        // sensitive key with exactly 3 chars -> no stars appended
        Object r2 = m.invoke(null, "authorization", "abc");
        assertThat(r2).isEqualTo("abc");

        // non-sensitive key: non-printable chars removed
        Object r3 = m.invoke(null, "x", "a\u0000b");
        assertThat(r3).isEqualTo("ab");
    }

    @Test
    @DisplayName("headerMapToString：null value becomes empty string")
    public void testHeaderMapToString_nullValue_becomesEmpty() {
        Map<String, String> m = new HashMap<>();
        m.put("k", null);
        String out = LogUtil.headerMapToString(m);
        assertThat(out).contains("k:");
    }

    @Test
    @DisplayName("headerMapToString: null, empty and non-empty map branches for compound condition coverage")
    public void testHeaderMapToString_null_empty_nonEmpty_branches() {
        // null map -> should return empty
        assertThat(LogUtil.headerMapToString(null)).isEmpty();

        // empty map -> should return empty (headerMap.isEmpty() == true)
        assertThat(LogUtil.headerMapToString(new HashMap<>())).isEmpty();

        // non-empty map -> should return non-empty string (headerMap.isEmpty() == false)
        Map<String, String> m = new HashMap<>();
        m.put("a", "1");
        String out = LogUtil.headerMapToString(m);
        assertThat(out).isEqualTo("a:1");
    }
}
