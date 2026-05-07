package com.example.inventory.hub.config;

import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 統合 Hub の設定。
 *
 * <p>{@code platform.hub.adapters.<name>} で各アダプタの設定を持つ。
 *
 * <p>{@code type=local}(default) は {@link
 * com.example.inventory.hub.adapter.out.file.LocalFileDestination}、 {@code type=s3} は {@link
 * com.example.inventory.hub.adapter.out.s3.S3Destination}(A3)。
 */
@ConfigurationProperties(prefix = "platform.hub")
public record HubProperties(Map<String, AdapterConfig> adapters) {

    public HubProperties {
        if (adapters == null) adapters = Map.of();
    }

    public AdapterConfig adapter(String name) {
        return adapters.getOrDefault(name, AdapterConfig.disabled());
    }

    /**
     * 単一アダプタの設定。
     *
     * <ul>
     *   <li>{@code enabled=false} で listener が起動しない
     *   <li>{@code type} = {@code local}(default)/ {@code s3}
     *   <li>{@code baseDir} / {@code fileName} は {@code type=local} で必要
     *   <li>{@code s3} は {@code type=s3} で必要
     * </ul>
     */
    public record AdapterConfig(
            boolean enabled, String type, String baseDir, String fileName, S3Config s3) {

        public AdapterConfig {
            if (type == null || type.isBlank()) type = "local";
        }

        public static AdapterConfig disabled() {
            return new AdapterConfig(false, "local", null, null, null);
        }
    }

    /** S3 destination 設定(A3)。 */
    public record S3Config(
            String bucket,
            String region,
            String keyPrefix,
            String endpointOverride,
            String contentType) {

        public S3Config {
            if (region == null || region.isBlank()) region = "ap-northeast-1";
            if (keyPrefix == null) keyPrefix = "";
            if (contentType == null || contentType.isBlank()) contentType = "text/csv";
        }
    }
}
