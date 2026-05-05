package com.example.inventory.commons.event;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Kafka DLQ(Dead Letter Queue)挙動の設定。
 *
 * <p>例:
 *
 * <pre>
 * platform:
 *   kafka:
 *     dlq:
 *       enabled: true
 *       suffix: .dlq
 *       max-retries: 3
 *       initial-interval-ms: 100
 *       multiplier: 2.0
 *       max-interval-ms: 2000
 * </pre>
 *
 * <p>{@code &lt;topic-name&gt;&lt;suffix&gt;} が DLQ トピック名(例: {@code audit.log.v1.dlq})。 DLQ
 * トピックは事前に作成しておくこと(本番では Terraform、開発では auto-create)。
 */
@ConfigurationProperties(prefix = "platform.kafka.dlq")
public record KafkaDlqProperties(
        boolean enabled,
        String suffix,
        int maxRetries,
        long initialIntervalMs,
        double multiplier,
        long maxIntervalMs) {

    public KafkaDlqProperties {
        if (suffix == null || suffix.isBlank()) {
            suffix = ".dlq";
        }
        if (maxRetries <= 0) {
            maxRetries = 3;
        }
        if (initialIntervalMs <= 0) {
            initialIntervalMs = 100L;
        }
        if (multiplier <= 1.0) {
            multiplier = 2.0;
        }
        if (maxIntervalMs <= 0) {
            maxIntervalMs = 2_000L;
        }
    }
}
