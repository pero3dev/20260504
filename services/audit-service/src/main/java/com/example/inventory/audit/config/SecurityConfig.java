package com.example.inventory.audit.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

import com.example.inventory.commons.security.SecurityFilterChainExempt;

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
    @SecurityFilterChainExempt(
            reason =
                    "audit-service は外部 API を持たず Kafka 内部消費 + actuator のみ。 actuator は K8s NetworkPolicy /"
                            + " Service Mesh で内部限定するためのアプリ層 permitAll で、 ADR-0023 revocation /"
                            + " RFC 7807 / TenantContextFilter は不要(JWT を受けない)。"
                            + " 将来 AuditAdminController に外部公開 API を出す段階で本 chain を 2 本化し SUPER_ADMIN 制限する設計。")
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http.csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(reg -> reg.anyRequest().permitAll())
                .build();
    }
}
