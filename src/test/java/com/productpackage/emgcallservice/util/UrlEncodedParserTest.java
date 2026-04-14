package com.productpackage.emgcallservice.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class UrlEncodedParserTest {

    private final UrlEncodedParser parser = new UrlEncodedParser();

    @Test
    @DisplayName("isValidUrlEncoded：null/空 返回 false")
    public void testIsValidUrlEncoded_nullOrEmpty_false() {
        assertThat(parser.isValidUrlEncoded(null)).isFalse();
        assertThat(parser.isValidUrlEncoded("")).isFalse();
    }

    @Test
    @DisplayName("parseToOrderedList：空字符串返回空 list，simple key=value 和 无 '=' 情形正确解析")
    public void testParseToOrderedList_emptyAndSimple() {
        List<UrlEncodedParser.Pair> empty = parser.parseToOrderedList("");
        assertThat(empty).isEmpty();

        List<UrlEncodedParser.Pair> pairs = parser.parseToOrderedList("a=1&b=2&c=");
        assertThat(pairs).hasSize(3);
        assertThat(pairs.get(0).key).isEqualTo("a");
        assertThat(pairs.get(0).value).isEqualTo("1");
        assertThat(pairs.get(1).key).isEqualTo("b");
        assertThat(pairs.get(1).value).isEqualTo("2");
        assertThat(pairs.get(2).key).isEqualTo("c");
        assertThat(pairs.get(2).value).isEqualTo("");

        List<UrlEncodedParser.Pair> noEq = parser.parseToOrderedList("keyWithoutEq");
        assertThat(noEq).hasSize(1);
        assertThat(noEq.get(0).key).isEqualTo("keyWithoutEq");
        assertThat(noEq.get(0).value).isEqualTo("");
    }

    @Test
    @DisplayName("serialize：序列化保持顺序、重复字段和编码")
    public void testSerialize_preservesOrderAndEncoding() {
        List<UrlEncodedParser.Pair> input = parser.parseToOrderedList("k=1&k=2&x=space+value");
        String serialized = parser.serialize(input);
        // parse -> serialize -> parse should be stable in terms of order/keys
        List<UrlEncodedParser.Pair> reparsed = parser.parseToOrderedList(serialized);
        assertThat(reparsed).hasSize(3);
        assertThat(reparsed.get(0).key).isEqualTo(input.get(0).key);
        assertThat(reparsed.get(0).value).isEqualTo(input.get(0).value);
        assertThat(reparsed.get(1).key).isEqualTo(input.get(1).key);
        assertThat(reparsed.get(1).value).isEqualTo(input.get(1).value);
        assertThat(reparsed.get(2).key).isEqualTo(input.get(2).key);
    }

    @Test
    @DisplayName("applyCoordinateRules：P_TYP 指示 hex 时替换为 invalidHex")
    public void testApplyCoordinateRules_withPTypHexMode_replaceLatLonWithHex() {
        List<UrlEncodedParser.Pair> entries = Arrays.asList(
                new UrlEncodedParser.Pair("P_TYP", "1"),
                new UrlEncodedParser.Pair("P_LAT1", "12.34"),
                new UrlEncodedParser.Pair("P_LON1", "56.78")
        );
        List<UrlEncodedParser.Pair> out = parser.applyCoordinateRules(entries, "FFFFFFFF", "999.9999999");
        assertThat(out).hasSize(3);
        assertThat(out.get(1).value).endsWith("FFFFFFFF");
        assertThat(out.get(2).value).endsWith("FFFFFFFF");
    }

    @Test
    @DisplayName("applyCoordinateRules：P_TYP 指示 decimal 时替换为 invalidDec")
    public void testApplyCoordinateRules_withPTypDecimalMode_replaceLatLonWithDec() {
        List<UrlEncodedParser.Pair> entries = Arrays.asList(
                new UrlEncodedParser.Pair("P_TYP", "0"),
                new UrlEncodedParser.Pair("P_LAT1", "12.34"),
                new UrlEncodedParser.Pair("P_LON1", "56.78")
        );
        List<UrlEncodedParser.Pair> out = parser.applyCoordinateRules(entries, "FFFFFFFF", "999.9999999");
        assertThat(out.get(1).value).endsWith("999.9999999");
        assertThat(out.get(2).value).endsWith("999.9999999");
    }

    @Test
    @DisplayName("applyCoordinateRules：缺少 P_TYP 时使用 invalidDec 替换")
    public void testApplyCoordinateRules_missingPTyp_replaceAllWithDec() {
        List<UrlEncodedParser.Pair> entries = Arrays.asList(
                new UrlEncodedParser.Pair("P_LAT1", "12.34"),
                new UrlEncodedParser.Pair("P_LON1", "56.78")
        );
        List<UrlEncodedParser.Pair> out = parser.applyCoordinateRules(entries, "FFFFFFFF", "999.9999999");
        assertThat(out.get(0).value).endsWith("999.9999999");
        assertThat(out.get(1).value).endsWith("999.9999999");
    }

    @Test
    @DisplayName("applyCoordinateRules：原值有方向前缀时保留该字母")
    public void testApplyCoordinateRules_prefixPreserved_keepDirectionLetter() {
        List<UrlEncodedParser.Pair> entries = Arrays.asList(
                new UrlEncodedParser.Pair("P_TYP", "0"),
                new UrlEncodedParser.Pair("P_LAT1", "N12.34"),
                new UrlEncodedParser.Pair("P_LON1", "E56.78")
        );
        List<UrlEncodedParser.Pair> out = parser.applyCoordinateRules(entries, "FFFFFFFF", "999.9999999");
        assertThat(out.get(1).value).startsWith("N");
        assertThat(out.get(2).value).startsWith("E");
    }

    @Test
    @DisplayName("decodeWithCharset：非法 charset 时返回原串")
    public void testDecodeWithCharset_invalidCharset_returnsInput() {
        String in = "a%20b";
        String out = parser.decodeWithCharset(in, "BAD-CHARSET");
        assertThat(out).isEqualTo(in);
    }

    @Test
    @DisplayName("encodeWithCharset：非法 charset 时返回原串")
    public void testEncodeWithCharset_invalidCharset_returnsInput() {
        String in = "a b";
        String out = parser.encodeWithCharset(in, "BAD-CHARSET");
        assertThat(out).isEqualTo(in);
    }

    @Test
    @DisplayName("isValidUrlEncoded：包含 '=' 或 '&' 返回 true")
    public void testIsValidUrlEncoded_containsEqualsOrAmpersand_true() {
        assertThat(parser.isValidUrlEncoded("a=b")).isTrue();
        assertThat(parser.isValidUrlEncoded("a&b")).isTrue();
    }

    @Test
    @DisplayName("parseToOrderedList：支持百分号解码与重复 key 保序")
    public void testParseToOrderedList_percentDecoding_and_repeatedKeys() {
        List<UrlEncodedParser.Pair> list = parser.parseToOrderedList("k%20=hello%2Bworld&k=2");
        assertThat(list).hasSize(2);
        assertThat(list.get(0).key).isEqualTo("k ");
        assertThat(list.get(0).value).isEqualTo("hello+world");
        assertThat(list.get(1).key).isEqualTo("k");
        assertThat(list.get(1).value).isEqualTo("2");
    }

    @Test
    @DisplayName("serialize：处理 null key 与 null value")
    public void testSerialize_handlesNullKeyAndNullValue() {
        List<UrlEncodedParser.Pair> entries = Arrays.asList(
                new UrlEncodedParser.Pair("a", "1"),
                new UrlEncodedParser.Pair(null, "v"),
                new UrlEncodedParser.Pair("k", null)
        );
        String s = parser.serialize(entries);
        assertThat(s).isEqualTo("a=1&=v&k=");
    }

    @Test
    @DisplayName("applyCoordinateRules：unparsable P_TYP 与 null/empty entries 场景")
    public void testApplyCoordinateRules_unparsablePTyp_and_nullEmpty() {
        List<UrlEncodedParser.Pair> src = Arrays.asList(
                new UrlEncodedParser.Pair("P_TYP", "notint"),
                new UrlEncodedParser.Pair("P_LAT1", "N1")
        );
        List<UrlEncodedParser.Pair> out = parser.applyCoordinateRules(src, "H", "D");
        assertThat(out.get(1).value).isEqualTo("ND");

        // null and empty
        assertThat(parser.applyCoordinateRules(null, "X", "Y")).isNull();
        assertThat(parser.applyCoordinateRules(Collections.emptyList(), "X", "Y")).isEmpty();
    }

    @Test
    @DisplayName("applyCoordinateRules：当经度/纬度值为 null 时也能替换为无效值")
    public void testApplyCoordinateRules_nullLatValue_replaced() {
        List<UrlEncodedParser.Pair> src = Arrays.asList(
                new UrlEncodedParser.Pair("P_TYP", "0"),
                new UrlEncodedParser.Pair("P_LAT1", null),
                new UrlEncodedParser.Pair("P_LON1", "")
        );
        List<UrlEncodedParser.Pair> out = parser.applyCoordinateRules(src, "HX", "DC");
        // null -> treated as empty -> replaced with invalidDec (because P_TYP=0 -> dec)
        assertThat(out.get(1).value).isEqualTo("DC");
        assertThat(out.get(2).value).isEqualTo("DC");
    }

    @Test
    @DisplayName("parseToOrderedList：处理空段以及等号在开头的情形")
    public void testParseToOrderedList_emptySegment_and_emptyKey() {
        List<UrlEncodedParser.Pair> l1 = parser.parseToOrderedList("a=1&&b=2&");
        // has an empty segment between && and trailing empty segment
        // Expect pairs: a=1, ""="", b=2, ""=""
        assertThat(l1).hasSize(4);
        assertThat(l1.get(1).key).isEqualTo("");

        List<UrlEncodedParser.Pair> l2 = parser.parseToOrderedList("=v");
        assertThat(l2).hasSize(1);
        assertThat(l2.get(0).key).isEqualTo("");
        assertThat(l2.get(0).value).isEqualTo("v");
    }

    @Test
    @DisplayName("parseToOrderedList：简单非空字符串分支覆盖")
    public void testParseToOrderedList_simpleNonEmpty() {
        List<UrlEncodedParser.Pair> l = parser.parseToOrderedList("x=y");
        assertThat(l).hasSize(1);
        assertThat(l.get(0).key).isEqualTo("x");
        assertThat(l.get(0).value).isEqualTo("y");
    }

    @Test
    @DisplayName("parseToOrderedList：在同一用例中依次覆盖 null/empty/non-empty 分支")
    public void testParseToOrderedList_nullEmptyNonEmpty_sameTest() {
        // call sequentially to ensure both branches of the initial null-check are exercised in same execution
        assertThat(parser.parseToOrderedList(null)).isEmpty();
        assertThat(parser.parseToOrderedList("")).isEmpty();
        assertThat(parser.parseToOrderedList("a=b")).hasSize(1);
    }

    @Test
    @DisplayName("private helpers isLat/isLon/pTypMatch via reflection for null/non-matching/matching")
    public void testPrivateHelpers_viaReflection() throws Exception {
        java.lang.reflect.Method isLat = UrlEncodedParser.class.getDeclaredMethod("isLat", String.class);
        java.lang.reflect.Method isLon = UrlEncodedParser.class.getDeclaredMethod("isLon", String.class);
        java.lang.reflect.Method pTyp = UrlEncodedParser.class.getDeclaredMethod("pTypMatch", String.class);
        isLat.setAccessible(true);
        isLon.setAccessible(true);
        pTyp.setAccessible(true);

        // isLat
        assertThat((Boolean) isLat.invoke(parser, "P_LAT1")).isTrue();
        assertThat((Boolean) isLat.invoke(parser, "X_LAT")).isFalse();
        assertThat((Boolean) isLat.invoke(parser, (Object) null)).isFalse();

        // isLon
        assertThat((Boolean) isLon.invoke(parser, "P_LON2")).isTrue();
        assertThat((Boolean) isLon.invoke(parser, "LON" )).isFalse();
        assertThat((Boolean) isLon.invoke(parser, (Object) null)).isFalse();

        // pTypMatch
        assertThat((Boolean) pTyp.invoke(parser, "P_TYP")).isTrue();
        assertThat((Boolean) pTyp.invoke(parser, "P_TYPE")).isFalse();
        assertThat((Boolean) pTyp.invoke(parser, (Object) null)).isFalse();
    }
}


