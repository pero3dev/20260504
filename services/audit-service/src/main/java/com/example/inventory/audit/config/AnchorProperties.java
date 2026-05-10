package com.example.inventory.audit.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Merkle anchor 自動計算 + 自動検証の設定(ADR-0008)。
 *
 * <p>{@code platform.audit.anchor.enabled=true} で daily anchor 計算 scheduler を有効化。 {@code tenants}
 * に空でないリストを与える必要がある(本 MVP は静的設定。本番は Identity Broker 経由 / DB 経由で動的取得に進化する)。 {@code cron} は Spring
 * Cron。既定は UTC 1:00 に「前日分」の anchor を計算する。
 *
 * <p>{@code platform.audit.anchor.verify.enabled=true} で daily 整合性検証 scheduler を有効化(A5
 * follow-up²⁵)。 計算 scheduler の後ろ(既定 UTC 2:00)に走り、 直近 {@code verify.lookback-days} 日分の anchor を
 * 再計算で再検証する。 mismatch は ERROR ログで Datadog 等のログモニタが拾う前提。 計算が無効なら検証も意味が無いため、 verify は計算の補助 toggle
 * として扱う。
 */
@ConfigurationProperties(prefix = "platform.audit.anchor")
public record AnchorProperties(boolean enabled, List<String> tenants, String cron, Verify verify) {

    public AnchorProperties {
        if (tenants == null) tenants = List.of();
        if (cron == null || cron.isBlank()) cron = "0 0 1 * * *";
        if (verify == null) verify = new Verify(false, null, 0);
    }

    /**
     * Daily integrity verification settings(ADR-0008、 A5 follow-up²⁵)。
     *
     * <p>{@code enabled=false} (default) で完全 no-op。 production で計算 scheduler を有効にしたら verify も有効化推奨。
     *
     * @param enabled scheduler を起動するか
     * @param cron Spring Cron。 default = UTC 2:00 毎日(計算 scheduler の 1 時間後)
     * @param lookbackDays 直近何日分の anchor を毎日検証するか。 default 7。 1 だと前日のみ、 30 だと過去 1 ヶ月
     */
    public record Verify(boolean enabled, String cron, int lookbackDays) {

        public Verify {
            if (cron == null || cron.isBlank()) cron = "0 0 2 * * *";
            if (lookbackDays <= 0) lookbackDays = 7;
        }
    }
}
