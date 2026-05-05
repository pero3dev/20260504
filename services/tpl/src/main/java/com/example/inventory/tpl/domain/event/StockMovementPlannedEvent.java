package com.example.inventory.tpl.domain.event;

import java.time.Instant;

import com.example.inventory.commons.event.DomainEvent;

/**
 * 入出庫が PLANNED として計上された事実。{@code tpl.stock.movement.v1} トピックへ Outbox 経由で発行される。
 *
 * <p>購読者: Inventory Core(在庫予定の反映)、Analytics(出入庫トレンド)、 Integration Hub(WMS / 委託元 ERP との同期)。
 *
 * <p>RECEIVED / DISPATCHED 確定時は別の {@code StockMovementConfirmedEvent} を発行する想定 (本 MVP では未実装)。
 */
public record StockMovementPlannedEvent(
        long aggregateId,
        String code,
        String partnerCode,
        String skuCode,
        String locationId,
        String movementType,
        int quantity,
        String referenceCode,
        long versionAfter,
        Instant occurredAt)
        implements DomainEvent {

    public static final String TOPIC = "tpl.stock.movement.v1";
    public static final String SCHEMA_VERSION = "1.0";

    @Override
    public String topic() {
        return TOPIC;
    }

    @Override
    public String schemaVersion() {
        return SCHEMA_VERSION;
    }
}
