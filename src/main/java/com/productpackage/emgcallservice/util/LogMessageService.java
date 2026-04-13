package com.productpackage.emgcallservice.util;

import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.stereotype.Component;

import java.text.MessageFormat;
import java.util.Locale;

/**
 * 用于从 properties 中加载日志模板并格式化。
 */
@Component
public class LogMessageService {

    private final MessageSource messageSource;

    public LogMessageService(final MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    /**
     * 根据 key 获取模板并用 args 格式化。
     *
     * @param key  模板 key
     * @param args 占位符参数
     * @return 格式化后的日志消息
     */
    public String format(final String key, final Object... args) {
        try {
            // 不再提供默认值为 key（否则会悄然返回 key），使用会在找不到消息时抛出 NoSuchMessageException
            final String pattern = messageSource.getMessage(key, null, Locale.getDefault());
            // pattern 由 messageSource 提供，MessageFormat 使用 {0},{1}... 占位
            return MessageFormat.format(pattern, args);
        } catch (final NoSuchMessageException nsme) {
            // 找不到模板时返回一个清晰的 fallback（以便排查）
            return key + " " + (args == null ? "" : java.util.Arrays.toString(args));
        } catch (final Exception e) {
            // 防止格式化异常导致日志失败，返回一个安全的 fallback
            return key + " " + (args == null ? "" : java.util.Arrays.toString(args));
        }
    }
}
