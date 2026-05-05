package com.example.inventory.audit.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Audit Service の Security 設定。
 *
 * <p>本サービスは外部公開の業務 API を持たず、actuator のみ存在する。 Kafka 経由で audit イベントを内部消費し、DB に書くだけのバックエンドサービス。
 * actuator は permit-all(本番では別途 NetworkPolicy / Service Mesh で内部に閉じる)。
 *
 * <p>commons-security の {@code PlatformSecurity} は使わない(JWT 検証不要、§17 に該当)。
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
