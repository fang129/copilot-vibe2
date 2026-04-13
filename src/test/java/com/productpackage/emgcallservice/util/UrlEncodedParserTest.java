package com.productpackage.emgcallservice.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
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
}


