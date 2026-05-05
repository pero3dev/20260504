package com.example.inventory.readmodel.adapter.in.kafka;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Kafka {@code inventory.movement.v1} トピックのペイロード型。 Inventory Core 側の {@code InventoryReservedEvent}
 * のフィールドと一対一対応する。
 *
 * <p>意図的に Inventory Core からインポートしていない: Read Model と Inventory Core はドメインを共有しない(マイクロサービスの境界)。
 * 接点はトピックスキーマ(Glue Schema Registry 統合後はこの DTO 自動生成に置換)。
 *
 * <p>{@code @JsonIgnoreProperties} で未知フィールド(将来追加されたもの)を許容する。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record InventoryMovementMessage(
        long aggregateId,
        String skuId,
        String locationId,
        long reservationId,
        int quantityReserved,
        int availableAfter,
        int reservedAfter,
        long versionAfter,
        Instant occurredAt) {}
