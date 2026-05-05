package com.example.inventory.commons.security;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import com.example.inventory.commons.tenant.TenantContextFilter;

/**
 * 13 サービスで共通の Security 設定をまとめて適用するヘルパ。
 *
 * <p>各サービスの {@code @Configuration SecurityConfig} は、 {@link #applyDefaults(HttpSecurity)}
 * で土台を作り、その上に固有の {@code authorizeHttpRequests(...)} を載せて {@link HttpSecurity#build()} する。
 *
 * <p>共通ポリシー:
 *
 * <ul>
 *   <li>CSRF / CORS は無効(REST API、CORS は API Gateway で吸収)
 *   <li>セッションは Stateless(JWT のみ)
 *   <li>OAuth2 リソースサーバ(JWT)+ {@code roles} クレームを {@code ROLE_*} へ変換
 *   <li>認証/認可エラーは RFC 7807 ProblemDetail JSON
 *   <li>{@link TenantContextFilter} を {@link BearerTokenAuthenticationFilter} の直後に挿入
 * </ul>
 */
public final class PlatformSecurity {

    private final AuthenticationEntryPoint entryPoint;
    private final AccessDeniedHandler accessDeniedHandler;
    private final TenantContextFilter tenantContextFilter;
    private final JwtAuthenticationConverter jwtConverter;

    public PlatformSecurity(
            AuthenticationEntryPoint entryPoint,
            AccessDeniedHandler accessDeniedHandler,
            TenantContextFilter tenantContextFilter,
            JwtAuthenticationConverter jwtConverter) {
        this.entryPoint = entryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
        this.tenantContextFilter = tenantContextFilter;
        this.jwtConverter = jwtConverter;
    }

    /**
     * プラットフォーム共通の HttpSecurity 設定を適用し、同じ {@link HttpSecurity} を返す。 呼出側は引き続き {@code
     * authorizeHttpRequests(...).build()} を続けて構成する。
     */
    public HttpSecurity applyDefaults(HttpSecurity http) throws Exception {
        return http.csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .oauth2ResourceServer(
                        oauth2 ->
                                oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter))
                                        .authenticationEntryPoint(entryPoint)
                                        .accessDeniedHandler(accessDeniedHandler))
                .exceptionHandling(
                        eh ->
                                eh.authenticationEntryPoint(entryPoint)
                                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterAfter(tenantContextFilter, BearerTokenAuthenticationFilter.class);
    }
}
