package com.example.inventory.core.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

import com.example.inventory.commons.security.PlatformSecurity;

/**
 * inventory-core の Security 設定。
 *
 * <p>共通部分(CSRF/CORS 無効、stateless、JWT、RFC 7807 エラー、TenantContextFilter 挿入)は {@link PlatformSecurity}
 * に集約済み(commons-security)。 本クラスはサービス固有の path matcher のみを担う。
 *
 * <p>{@code loadtest} profile では本 Bean は生成されず、{@link LoadTestSecurityConfig} 側の permitAll な filter
 * chain が使われる(load-test/README.md 参照)。
 */
@Configuration
@Profile("!loadtest")
public class SecurityConfig {

    private static final String[] PERMIT_ALL = {"/actuator/health/**", "/actuator/info", "/error"};

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, PlatformSecurity platform)
            throws Exception {
        return platform.applyDefaults(http)
                .authorizeHttpRequests(
                        reg ->
                                reg.requestMatchers(PERMIT_ALL)
                                        .permitAll()
                                        .anyRequest()
                                        .authenticated())
                .build();
    }
}
