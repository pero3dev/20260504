package com.example.inventory.commons.error;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * commons-error のオートコンフィグ。{@link GlobalExceptionHandler} を 自動登録する。{@code
 * platform.error.global-handler.enabled=false} で無効化可能。
 */
@AutoConfiguration
@ConditionalOnClass(RestControllerAdvice.class)
@ConditionalOnProperty(
        prefix = "platform.error.global-handler",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class ErrorAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }
}
