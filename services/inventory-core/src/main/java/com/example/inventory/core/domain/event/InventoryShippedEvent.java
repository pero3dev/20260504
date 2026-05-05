package com.example.inventory.core.domain.event;

import java.time.Instant;

import com.example.inventory.commons.event.DomainEvent;

/**
 * Inventory 集約の {@code ship()} 成功時に発行される。{@code inventory.movement.v1} トピックへ Outbox 経由で発行。
 *
 * <p>引当済み(reserved)から実際の出荷で在庫が消化された事実を表す。 {@code reserved} は減少するが {@code available}
 * は変わらない(reservation 時に既に available から差し引かれている)。
 */
public record InventoryShippedEvent(
        long aggregateId,
        String skuId,
        String locationId,
        /** この出荷で消化した数量。 */
        int quantityShipped,
        int availableAfter,
        int reservedAfter,
        long versionAfter,
        Instant occurredAt)
        implements DomainEvent {

    public static final String TOPIC = "inventory.movement.v1";
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
