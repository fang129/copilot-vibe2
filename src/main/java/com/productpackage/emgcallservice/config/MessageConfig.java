package com.productpackage.emgcallservice.config;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;

/**
 * MessageSource 配置，用于加载 log-message.properties 并支持热加载（cacheSeconds 可调整）。
 */
@Configuration
public class MessageConfig {

    @Bean
    @Primary
    public MessageSource messageSource() {
        final ReloadableResourceBundleMessageSource ms = new ReloadableResourceBundleMessageSource();
        ms.setBasename("classpath:log-message");
        ms.setDefaultEncoding("UTF-8");
        // 开发环境可设置小的 cacheSeconds；生产建议 -1（永久缓存）
        ms.setCacheSeconds(10);
        return ms;
    }
}

