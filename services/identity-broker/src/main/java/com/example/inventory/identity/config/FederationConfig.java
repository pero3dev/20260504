package com.example.inventory.identity.config;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Federation 関連の Bean 配線。 {@code platform.identity.federation.jit.*} を {@link
 * FederationJitProperties} record に詰めて DI 可能にする。
 *
 * <p>{@code group-role-mappings} は Map なので {@link Binder} で構造化バインドする。 {@code @Value} の単純文字列バインドでは
 * Map をサポートしないため。
 */
@Configuration
public class FederationConfig {

    private static final String GROUP_ROLE_MAPPINGS_PROP =
            "platform.identity.federation.jit.group-role-mappings";

    @Bean
    public FederationJitProperties federationJitProperties(
            @Value("${platform.identity.federation.jit.enabled:false}") boolean enabled,
            @Value("${platform.identity.federation.jit.default-tenant-id:}") String defaultTenantId,
            @Value("${platform.identity.federation.jit.default-role:VIEWER}") String defaultRole,
            Environment environment) {
        Map<String, String> groupRoleMappings =
                Binder.get(environment)
                        .bind(GROUP_ROLE_MAPPINGS_PROP, Bindable.mapOf(String.class, String.class))
                        .orElseGet(Map::of);
        return new FederationJitProperties(
                enabled, defaultTenantId, defaultRole, groupRoleMappings);
    }
}
