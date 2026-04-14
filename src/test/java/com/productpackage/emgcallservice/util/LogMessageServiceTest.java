package com.productpackage.emgcallservice.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Tests for LogMessageService to cover normal formatting and exception fallback paths.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LogMessageService 单元测试")
class LogMessageServiceTest {

    @Mock
    private MessageSource messageSource;

    @Test
    @DisplayName("testFormat_Normal_ReturnsFormatted")
    void testFormat_Normal_ReturnsFormatted() {
        final LogMessageService svc = new LogMessageService(messageSource);
        // 模拟 messageSource 返回带占位符的模板
        when(messageSource.getMessage("greeting", null, Locale.getDefault()))
                .thenReturn("Hello {0} {1}!");

        final String result = svc.format("greeting", "Alice", "Smith");
        assertThat(result).isEqualTo("Hello Alice Smith!");
    }

    @Test
    @DisplayName("testFormat_NoSuchMessage_ReturnsFallbackWithArgs")
    void testFormat_NoSuchMessage_ReturnsFallbackWithArgs() {
        final LogMessageService svc = new LogMessageService(messageSource);
        // 模拟 messageSource 抛出 NoSuchMessageException
        when(messageSource.getMessage("missing.key", null, Locale.getDefault()))
                .thenThrow(new NoSuchMessageException("missing.key"));

        final String result = svc.format("missing.key", "A", 123);
        // 根据实现，fallback = key + " " + Arrays.toString(args)
        assertThat(result).isEqualTo("missing.key " + java.util.Arrays.toString(new Object[]{"A", 123}));
    }

    @Test
    @DisplayName("testFormat_NoSuchMessage_ReturnsFallbackWhenArgsNull")
    void testFormat_NoSuchMessage_ReturnsFallbackWhenArgsNull() {
        final LogMessageService svc = new LogMessageService(messageSource);
        // 模拟 messageSource 抛出 NoSuchMessageException
        when(messageSource.getMessage("missing.key", null, Locale.getDefault()))
                .thenThrow(new NoSuchMessageException("missing.key"));

        // args 为 null 时，根据实现，返回 key + " "
        final String result = svc.format("missing.key", (Object[]) null);
        assertThat(result).isEqualTo("missing.key ");
    }

    @Test
    @DisplayName("testFormat_OtherException_ReturnsFallbackWhenArgsNull")
    void testFormat_OtherException_ReturnsFallbackWhenArgsNull() {
        final LogMessageService svc = new LogMessageService(messageSource);
        // 模拟 messageSource 抛出其他运行时异常
        when(messageSource.getMessage("err.key", null, Locale.getDefault()))
                .thenThrow(new RuntimeException("boom"));

        // args 为 null 时，根据实现，返回 key + " "
        final String result = svc.format("err.key", (Object[]) null);
        assertThat(result).isEqualTo("err.key ");
    }

    @Test
    @DisplayName("testFormat_MessageFormatThrows_ReturnsFallbackWithArgs")
    void testFormat_MessageFormatThrows_ReturnsFallbackWithArgs() {
        final LogMessageService svc = new LogMessageService(messageSource);
        // messageSource returns an invalid pattern that causes MessageFormat.format to throw
        when(messageSource.getMessage("bad.pattern", null, Locale.getDefault()))
                .thenReturn("{"); // invalid pattern

        final String result = svc.format("bad.pattern", "X");
        // fallback should include the args when args != null
        assertThat(result).isEqualTo("bad.pattern " + java.util.Arrays.toString(new Object[]{"X"}));
    }
}
