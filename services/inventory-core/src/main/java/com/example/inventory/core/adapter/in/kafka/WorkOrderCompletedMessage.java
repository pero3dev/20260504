package com.example.inventory.core.adapter.in.kafka;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * {@code manufacturing.work_order.completed.v1} ペイロード。 Manufacturing 側 {@code
 * WorkOrderCompletedEvent} と項目構造を一致させる。
 *
 * <p>{@code completedAt} は Inventory Core の入庫処理では使用しないが、同一スキーマで読めるようフィールドを保持する
 * ({@code @JsonIgnoreProperties(ignoreUnknown = true)} で 将来の追加項目を許容)。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WorkOrderCompletedMessage(
        long aggregateId,
        String code,
        String productSkuCode,
        String locationId,
        int completedQuantity,
        Instant completedAt,
        Instant occurredAt) {}
