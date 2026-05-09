package com.example.inventory.identity.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Federation 関連の Bean 配線。 {@code platform.identity.federation.jit.*} を {@link
 * FederationJitProperties} record に詰めて DI 可能にする。
 */
@Configuration
public class FederationConfig {

    @Bean
    public FederationJitProperties federationJitProperties(
            @Value("${platform.identity.federation.jit.enabled:false}") boolean enabled,
            @Value("${platform.identity.federation.jit.default-tenant-id:}")
                    String defaultTenantId,
            @Value("${platform.identity.federation.jit.default-role:VIEWER}") String defaultRole) {
        return new FederationJitProperties(enabled, defaultTenantId, defaultRole);
    }
}
