package com.productpackage.emgcallservice.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LogUtil 单元测试")
class LogUtilTest {

    @Test
    @DisplayName("testToBase64_String_NullOrEmpty_ReturnsEmpty")
    void testToBase64_String_NullOrEmpty_ReturnsEmpty() {
        assertThat(LogUtil.toBase64((String) null)).isEqualTo("");
        assertThat(LogUtil.toBase64("")).isEqualTo("");
    }

    @Test
    @DisplayName("testToBase64_String_NormalString_ReturnsBase64")
    void testToBase64_String_NormalString_ReturnsBase64() {
        String encoded = LogUtil.toBase64("abc");
        assertThat(encoded).isEqualTo(java.util.Base64.getEncoder().encodeToString("abc".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("testToBase64_Bytes_NullOrEmpty_ReturnsEmpty")
    void testToBase64_Bytes_NullOrEmpty_ReturnsEmpty() {
        assertThat(LogUtil.toBase64((byte[]) null)).isEqualTo("");
        assertThat(LogUtil.toBase64(new byte[0])).isEqualTo("");
    }

    @Test
    @DisplayName("testToBase64_Bytes_Normal_ReturnsBase64")
    void testToBase64_Bytes_Normal_ReturnsBase64() {
        byte[] data = {1, 2, 3};
        assertThat(LogUtil.toBase64(data)).isEqualTo(java.util.Base64.getEncoder().encodeToString(data));
    }

    @Test
    @DisplayName("testTruncate_Null_ReturnsEmpty")
    void testTruncate_Null_ReturnsEmpty() {
        assertThat(LogUtil.truncate(null, 10)).isEqualTo("");
    }

    @Test
    @DisplayName("testTruncate_ShorterThanMax_ReturnsOriginal")
    void testTruncate_ShorterThanMax_ReturnsOriginal() {
        String s = "short";
        assertThat(LogUtil.truncate(s, 10)).isEqualTo(s);
    }

    @Test
    @DisplayName("testTruncate_LongerThanMax_ReturnsTruncated")
    void testTruncate_LongerThanMax_ReturnsTruncated() {
        String s = "abcdefghijklmnopqrstuvwxyz";
        assertThat(LogUtil.truncate(s, 5)).isEqualTo("abcde...(truncated)");
    }

    @Test
    @DisplayName("testTruncate_DefaultOverload_ReturnsOriginalWhenShort")
    void testTruncate_DefaultOverload_ReturnsOriginalWhenShort() {
        String s = "ok";
        assertThat(LogUtil.truncate(s)).isEqualTo(s);
    }

    @Test
    @DisplayName("testHeaderMapToString_NullOrEmpty_ReturnsEmpty")
    void testHeaderMapToString_NullOrEmpty_ReturnsEmpty() {
        assertThat(LogUtil.headerMapToString(null)).isEqualTo("");
        assertThat(LogUtil.headerMapToString(new LinkedHashMap<>())).isEqualTo("");
    }

    @Test
    @DisplayName("testHeaderMapToString_SensitiveHeader_IsMasked")
    void testHeaderMapToString_SensitiveHeader_IsMasked() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("U_ID", "123456789"); // case-insensitive sensitive header
        String out = LogUtil.headerMapToString(m);
        // keep first 3 chars, rest replaced by '*'
        assertThat(out).isEqualTo("U_ID:123******");
    }

    @Test
    @DisplayName("testHeaderMapToString_NonPrintableChars_Removed")
    void testHeaderMapToString_NonPrintableChars_Removed() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("x-custom", "a\u0000b\u0001c"); // contains control chars
        String out = LogUtil.headerMapToString(m);
        assertThat(out).isEqualTo("x-custom:abc");
    }

    @Test
    @DisplayName("testHeaderMapToString_KeyOrValueNull_Handled")
    void testHeaderMapToString_KeyOrValueNull_Handled() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put(null, null); // normKey -> "" and value -> ""
        m.put("normal", null); // value -> ""
        String out = LogUtil.headerMapToString(m);
        // order preserved because LinkedHashMap: first entry becomes ":" then ;normal:
        assertThat(out).isEqualTo(":;normal:");
    }

    @Test
    @DisplayName("testMaskIfSensitive_keyNull_returnsValue_viaReflection")
    void testMaskIfSensitive_keyNull_returnsValue_viaReflection() throws Exception {
        // maskIfSensitive is private; call via reflection to exercise the key==null branch
        final java.lang.reflect.Method m = LogUtil.class.getDeclaredMethod("maskIfSensitive", String.class, String.class);
        m.setAccessible(true);
        final String res = (String) m.invoke(null, (Object) null, "secret");
        assertThat(res).isEqualTo("secret");
    }

    @Test
    @DisplayName("testHeaderMapToString_ShortSensitiveValue_NoStars")
    void testHeaderMapToString_ShortSensitiveValue_NoStars() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("authorization", "ab"); // sensitive but shorter than 3
        String out = LogUtil.headerMapToString(m);
        assertThat(out).isEqualTo("authorization:ab");
    }
}
