package com.example.inventory.audit.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Merkle anchor 自動計算の設定(ADR-0008)。
 *
 * <p>{@code platform.audit.anchor.enabled=true} で daily anchor scheduler を有効化。 {@code tenants}
 * に空でないリストを与える必要がある(本 MVP は静的設定。本番は Identity Broker 経由 / DB 経由で動的取得に進化する)。
 *
 * <p>{@code cron} は Spring Cron。既定は UTC 1:00 に「前日分」の anchor を計算する。
 */
@ConfigurationProperties(prefix = "platform.audit.anchor")
public record AnchorProperties(boolean enabled, List<String> tenants, String cron) {

    public AnchorProperties {
        if (tenants == null) tenants = List.of();
        if (cron == null || cron.isBlank()) cron = "0 0 1 * * *";
    }
}
