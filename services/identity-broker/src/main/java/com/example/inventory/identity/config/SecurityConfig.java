package com.example.inventory.identity.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

import com.example.inventory.commons.security.PlatformSecurity;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;

/**
 * Identity Broker の Security 設定。
 *
 * <p>本サービスは「JWT を発行する側」だが、 自分が発行した JWT を {@code /v1/admin/**} で SUPER_ADMIN role に絞って 検証する用途のため、 自己
 * JWK を使う {@link JwtDecoder} を Bean 提供する(JWKS HTTP 経路を踏まずに in-process で完結)。
 *
 * <p>SecurityFilterChain は 2 本に分割:
 *
 * <ul>
 *   <li>{@code adminFilterChain}(Order 1、 {@code /v1/admin/**}):commons-security の {@link
 *       PlatformSecurity#applyDefaults} で JWT 検証 + RFC 7807 認証/認可エラー + TenantContextFilter を有効化し、
 *       さらに {@code hasRole("SUPER_ADMIN")} で SUPER_ADMIN 権限必須にする
 *   <li>{@code publicFilterChain}(Order 2、 残り全部):認証無し(MVP)。 ログイン / 交換 / JWKS / actuator 等。 Bearer
 *       ヘッダがあっても無視され、 普通の permitAll として処理される(oauth2ResourceServer を adapter として持たないため、 stale Bearer
 *       による 401 漏れを起こさない)
 * </ul>
 *
 * <p>SUPER_ADMIN role の provisioning 経路は本 phase では別途運用(プラットフォーム管理用テナントの membership に {@code
 * SUPER_ADMIN} を含む roles で行を入れる)。 web UI 化は次 phase 課題。
 */
@Configuration
public class SecurityConfig {

    /** identity-broker 自身が発行した JWT を、 自前 JWK で in-process 検証する {@link JwtDecoder}。 */
    @Bean
    public JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
        ConfigurableJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
        processor.setJWSKeySelector(
                new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, jwkSource));
        return new NimbusJwtDecoder(processor);
    }

    /** {@code /v1/admin/**} のみ JWT + SUPER_ADMIN role を必須化。 */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public SecurityFilterChain adminFilterChain(HttpSecurity http, PlatformSecurity platform)
            throws Exception {
        return platform.applyDefaults(http.securityMatcher("/v1/admin/**"))
                .authorizeHttpRequests(reg -> reg.anyRequest().hasRole("SUPER_ADMIN"))
                .build();
    }

    /** 認証/JWKS/Actuator など admin 以外の全パス。 認証無し(MVP)。 */
    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    public SecurityFilterChain publicFilterChain(HttpSecurity http) throws Exception {
        return http.csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(reg -> reg.anyRequest().permitAll())
                .build();
    }
}
