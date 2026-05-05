package com.example.inventory.commons.event;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * OutboxPublisher の設定。サービスは {@code application.yml} で上書きできる。
 *
 * <p>例:
 *
 * <pre>
 * platform:
 *   outbox:
 *     poll-interval: 1s
 *     batch-size: 200
 *     tenants:
 *       - acme
 *       - beta-inc
 * </pre>
 */
@ConfigurationProperties(prefix = "platform.outbox")
public record OutboxProperties(
        /** 走査周期。 */
        Duration pollInterval,
        /** 1テナント・1周期あたりの最大取得件数。 */
        int batchSize,
        /** 設定ベースの TenantDirectory が返すテナントID一覧(将来は動的解決に置換)。 */
        List<String> tenants) {

    public OutboxProperties {
        if (pollInterval == null) {
            pollInterval = Duration.ofSeconds(1);
        }
        if (batchSize <= 0) {
            batchSize = 200;
        }
        if (tenants == null) {
            tenants = List.of();
        }
    }
}
