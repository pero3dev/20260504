package com.example.inventory.hub.config;

import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 統合 Hub の設定。
 *
 * <p>{@code platform.hub.adapters.<name>} で各アダプタの設定を持つ。 MVP は {@code retail-order-csv} のみ。
 */
@ConfigurationProperties(prefix = "platform.hub")
public record HubProperties(Map<String, AdapterConfig> adapters) {

    public HubProperties {
        if (adapters == null) adapters = Map.of();
    }

    public AdapterConfig adapter(String name) {
        return adapters.getOrDefault(name, AdapterConfig.disabled());
    }

    /** 単一アダプタの設定。{@code enabled = false} でスキップ。 */
    public record AdapterConfig(boolean enabled, String baseDir, String fileName) {

        public static AdapterConfig disabled() {
            return new AdapterConfig(false, null, null);
        }
    }
}
