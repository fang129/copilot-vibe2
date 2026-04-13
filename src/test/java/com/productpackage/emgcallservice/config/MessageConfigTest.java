package com.productpackage.emgcallservice.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

public class MessageConfigTest {

    @Test
    @DisplayName("messageSource：返回 ReloadableResourceBundleMessageSource 且能加载 log-message.properties 中的 key")
    public void testMessageSource_loadsMessages_and_hasExpectedSettings() {
        final MessageConfig cfg = new MessageConfig();
        final MessageSource ms = cfg.messageSource();
        assertThat(ms).isInstanceOf(ReloadableResourceBundleMessageSource.class);

        final ReloadableResourceBundleMessageSource r = (ReloadableResourceBundleMessageSource) ms;

        // 只能断言类型并验证可以加载资源文件中的模板进行格式化（部分内部属性为 protected/不可见）
        final String formatted = ms.getMessage("log.requestReceived", new Object[]{"FCODE", "GET", "/p", "INVID"}, Locale.getDefault());
        assertThat(formatted).contains("FCODE").contains("GET").contains("/p").contains("INVID");
    }
}


