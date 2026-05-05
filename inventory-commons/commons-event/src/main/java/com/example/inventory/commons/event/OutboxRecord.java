package com.example.inventory.commons.event;

import java.time.Instant;

/**
 * {@code outbox} テーブルの1レコード。集約の保存と原子的に書き込まれ、 その後 {@link OutboxPublisher} によって Kafka へドレインされる。
 *
 * <p>{@code published} はブローカ ack 後に true に更新される。 発行プロセスの再起動時にも欠落は無いが、再発行は起こり得るため、 コンシューマ側は {@code
 * eventId} に対して冪等であること。
 */
public record OutboxRecord(
        long eventId,
        String tenantId,
        String topic,
        String schemaVersion,
        long aggregateId,
        String payloadJson,
        String traceId,
        Instant occurredAt,
        Instant createdAt,
        boolean published) {}
