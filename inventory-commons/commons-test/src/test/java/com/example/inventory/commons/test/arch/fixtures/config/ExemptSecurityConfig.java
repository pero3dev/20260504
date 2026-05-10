package com.example.inventory.commons.test.arch.fixtures.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.web.SecurityFilterChain;

import com.example.inventory.commons.security.SecurityFilterChainExempt;

/**
 * {@link com.example.inventory.commons.test.arch.SecurityRules} の例外宣言系 fixture。 applyDefaults
 * を呼ばないが {@link SecurityFilterChainExempt} で明示宣言しているので合格する。
 */
@Configuration
public class ExemptSecurityConfig {

    @Bean
    @SecurityFilterChainExempt(reason = "fixture: 単体テスト用に意図的に bypass")
    public SecurityFilterChain exemptFilterChain() {
        return null;
    }
}
