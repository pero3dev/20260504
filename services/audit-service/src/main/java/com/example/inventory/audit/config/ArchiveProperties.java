package com.example.inventory.audit.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * S3 への audit 保管設定(ADR-0008)。
 *
 * <p>{@code platform.audit.archive.enabled=true} で S3 export が有効化される。 デフォルトは無効 (test/loadtest/local
 * 環境で AWS credential 無しでも動かせるように)。
 *
 * <p>{@code endpointOverride} は LocalStack や MinIO をローカル開発で使うためのもの。 本番では空にして AWS 標準 endpoint。
 * {@code recordsPrefix} / {@code anchorsPrefix} は同一 bucket 内で record と anchor の prefix を分けるための設定。
 */
@ConfigurationProperties(prefix = "platform.audit.archive")
public record ArchiveProperties(
        boolean enabled,
        String bucket,
        String region,
        String endpointOverride,
        String recordsPrefix,
        String anchorsPrefix) {

    public ArchiveProperties {
        if (region == null || region.isBlank()) region = "ap-northeast-1";
        if (recordsPrefix == null) recordsPrefix = "audit-records";
        if (anchorsPrefix == null) anchorsPrefix = "audit-anchors";
        if (enabled && (bucket == null || bucket.isBlank())) {
            throw new IllegalArgumentException(
                    "platform.audit.archive.enabled=true の場合は bucket は必須");
        }
    }
}
