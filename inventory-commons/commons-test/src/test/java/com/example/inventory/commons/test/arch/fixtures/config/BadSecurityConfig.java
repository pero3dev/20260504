package com.example.inventory.commons.test.arch.fixtures.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.web.SecurityFilterChain;

/**
 * {@link com.example.inventory.commons.test.arch.SecurityRules} の違反系 fixture。 applyDefaults を呼ばず、
 * {@code @SecurityFilterChainExempt} も無いので CI 違反として検出されること。
 */
@Configuration
public class BadSecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain() {
        return null;
    }
}
