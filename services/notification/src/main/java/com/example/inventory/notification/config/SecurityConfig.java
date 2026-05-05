package com.example.inventory.notification.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Notification の Security 設定。
 *
 * <p>本サービスは外部公開の業務 API を持たず、actuator のみ。 Kafka 経由で業務イベントを内部消費し、DB に書いてメール送信するバックエンドサービス。 actuator は
 * permit-all(本番では NetworkPolicy / Service Mesh で内部に閉じる)。 commons-security の {@code
 * PlatformSecurity} は使わない(JWT 検証不要)。
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http.csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(reg -> reg.anyRequest().permitAll())
                .build();
    }
}
