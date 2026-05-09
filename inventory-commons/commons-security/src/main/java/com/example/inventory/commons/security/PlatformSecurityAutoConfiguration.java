package com.example.inventory.commons.security;

import java.util.Collection;
import java.util.List;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

import com.example.inventory.commons.tenant.TenantContextFilter;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * commons-security のオートコンフィグ。 Spring Security web がクラスパスにあるサービスでは依存追加だけで以下を提供する:
 *
 * <ul>
 *   <li>{@link AuthenticationEntryPoint}({@link ProblemAuthenticationEntryPoint})
 *   <li>{@link AccessDeniedHandler}({@link ProblemAccessDeniedHandler})
 *   <li>{@link JwtAuthenticationConverter}({@code roles} → {@code ROLE_*})
 *   <li>{@link TenantContextFilter}
 *   <li>{@link PlatformSecurity}(SecurityFilterChain 構成のヘルパ)
 * </ul>
 *
 * <p>{@link SecurityFilterChain} 自体はサービスごとに permitAll パスや特殊ルールが異なるため、 commons では提供しない。各サービスの {@code
 * SecurityConfig} で {@link
 * PlatformSecurity#applyDefaults(org.springframework.security.config.annotation.web.builders.HttpSecurity)}
 * を呼び出して構成する。
 */
@AutoConfiguration(
        after = {
            RedisRevocationStoreAutoConfiguration.class,
            NoOpRevocationStoreAutoConfiguration.class
        })
@ConditionalOnClass(SecurityFilterChain.class)
public class PlatformSecurityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public AuthenticationEntryPoint authenticationEntryPoint(ObjectMapper objectMapper) {
        return new ProblemAuthenticationEntryPoint(objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public AccessDeniedHandler accessDeniedHandler(ObjectMapper objectMapper) {
        return new ProblemAccessDeniedHandler(objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(
                PlatformSecurityAutoConfiguration::rolesToAuthorities);
        return converter;
    }

    @Bean
    @ConditionalOnMissingBean
    public TenantContextFilter tenantContextFilter() {
        return new TenantContextFilter();
    }

    @Bean
    @ConditionalOnMissingBean
    public PlatformSecurity platformSecurity(
            AuthenticationEntryPoint entryPoint,
            AccessDeniedHandler accessDeniedHandler,
            TenantContextFilter tenantContextFilter,
            JwtAuthenticationConverter jwtConverter,
            RevocationCheckFilter revocationCheckFilter) {
        return new PlatformSecurity(
                entryPoint,
                accessDeniedHandler,
                tenantContextFilter,
                jwtConverter,
                revocationCheckFilter);
    }

    @Bean
    @ConditionalOnMissingBean
    public RevocationCheckFilter revocationCheckFilter(
            RevocationStore revocationStore, AuthenticationEntryPoint entryPoint) {
        return new RevocationCheckFilter(revocationStore, entryPoint);
    }

    /** JWT の {@code roles} クレーム(配列)を {@code ROLE_*} 権限に変換する。 */
    private static Collection<GrantedAuthority> rolesToAuthorities(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        if (roles == null) {
            return List.of();
        }
        return roles.stream()
                .<GrantedAuthority>map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                .toList();
    }
}
