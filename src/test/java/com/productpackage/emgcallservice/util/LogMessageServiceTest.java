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

@ExtendWith(MockitoExtension.class)
public class LogMessageServiceTest {

    @Mock
    private MessageSource messageSource;

    @Test
    @DisplayName("format：messageSource 有模板时返回格式化字符串")
    public void testFormat_messageFound_returnsFormatted() {
        when(messageSource.getMessage("key.test", null, Locale.getDefault())).thenReturn("hello {0}");
        LogMessageService svc = new LogMessageService(messageSource);
        String out = svc.format("key.test", "world");
        assertThat(out).isEqualTo("hello world");
    }

    @Test
    @DisplayName("format：messageSource 抛 NoSuchMessageException 时返回清晰 fallback")
    public void testFormat_messageNotFound_returnsFallbackString() {
        when(messageSource.getMessage("no.key", null, Locale.getDefault())).thenThrow(new NoSuchMessageException("no.key"));
        LogMessageService svc = new LogMessageService(messageSource);
        String out = svc.format("no.key", "a", 1);
        assertThat(out).startsWith("no.key");
        assertThat(out).contains("a");
    }

    @Test
    @DisplayName("format：messageSource 抛其他异常时返回安全 fallback")
    public void testFormat_formattingException_safeFallback() {
        when(messageSource.getMessage("bad", null, Locale.getDefault())).thenThrow(new RuntimeException("boom"));
        LogMessageService svc = new LogMessageService(messageSource);
        String out = svc.format("bad", 1, 2);
        assertThat(out).startsWith("bad");
    }
}

