package com.example.inventory.commons.test.arch.fixtures.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

import com.example.inventory.commons.security.PlatformSecurity;

/**
 * {@link com.example.inventory.commons.test.arch.SecurityRules} の正常系 fixture。 applyDefaults
 * を呼ぶので合格する。
 */
@Configuration
public class GoodSecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, PlatformSecurity platform)
            throws Exception {
        return platform.applyDefaults(http).build();
    }
}
