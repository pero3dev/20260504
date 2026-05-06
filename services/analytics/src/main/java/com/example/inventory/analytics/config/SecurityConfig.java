package com.example.inventory.analytics.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

import com.example.inventory.commons.security.PlatformSecurity;

@Configuration
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
