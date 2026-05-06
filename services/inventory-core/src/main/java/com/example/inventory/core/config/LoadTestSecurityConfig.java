package com.example.inventory.core.config;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import com.example.inventory.commons.tenant.TenantContext;
import com.example.inventory.commons.tenant.TenantId;

/**
 * 負荷試験(load-test/)用 Security 設定。
 *
 * <p>本 Bean は {@code spring.profiles.active=loadtest} の時のみ有効化され、 通常 profile では {@link
 * SecurityConfig} が使われる。
 *
 * <p>動作:
 *
 * <ul>
 *   <li>JWT 認証を完全に bypass(全 endpoint permitAll)
 *   <li>HTTP header {@code X-Tenant-Id} から tenant_id を取得し {@link TenantContext} に束ねる (本来は JWT claim
 *       から取得するが、 load-test では token 発行を省略する代わりに header で渡す)
 *   <li>{@code X-Tenant-Id} 未指定の場合は {@code dev} を既定値とする
 * </ul>
 *
 * <p>⚠️ 本 profile を本番環境で有効にしてはならない(認証バイパスのため)。 production image では Spring profile を {@code
 * loadtest} にしないこと、 K8s ConfigMap でも誤設定を防ぐこと。
 */
@Configuration
@Profile("loadtest")
public class LoadTestSecurityConfig {

    private static final Logger LOG = LoggerFactory.getLogger(LoadTestSecurityConfig.class);

    @Bean
    public SecurityFilterChain loadTestFilterChain(HttpSecurity http) throws Exception {
        LOG.warn(
                "==========================================\n"
                        + " ⚠️  LOADTEST PROFILE: 認証バイパス有効。本番禁止。\n"
                        + "==========================================");
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(reg -> reg.anyRequest().permitAll())
                .addFilterBefore(
                        new LoadTestTenantHeaderFilter(),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * {@code X-Tenant-Id} header から tenant_id を取得して {@link TenantContext} を埋める。 通常 profile の {@code
     * TenantContextFilter}(JWT claim 経由)の load-test 代替。
     */
    static final class LoadTestTenantHeaderFilter extends OncePerRequestFilter {

        private static final String HEADER = "X-Tenant-Id";
        private static final String DEFAULT_TENANT = "dev";

        @Override
        protected void doFilterInternal(
                HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            String value = request.getHeader(HEADER);
            String tenantId = (value == null || value.isBlank()) ? DEFAULT_TENANT : value;
            try {
                TenantContext.set(new TenantId(tenantId));
                chain.doFilter(request, response);
            } finally {
                TenantContext.clear();
            }
        }
    }
}
