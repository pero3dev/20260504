package com.example.inventory.core.domain.event;

import java.time.Instant;

import com.example.inventory.commons.event.DomainEvent;

/**
 * Inventory 集約の {@code receive()} 成功時に発行される。{@code inventory.movement.v1} トピックへ Outbox 経由で発行。
 *
 * <p>入荷により {@code available} が増加した事実を表す。
 *
 * <p>{@link InventoryReservedEvent} と同じトピックに乗る。Read Model 等の購読者は post-state ({@code availableAfter}
 * / {@code reservedAfter} / {@code versionAfter})から状態を再構築できるため、 イベント種別を区別する必要は無い(購読者が関心ある場合は {@code
 * quantityReceived} の有無で判別可能)。
 */
public record InventoryReceivedEvent(
        long aggregateId,
        String skuId,
        String locationId,
        /** この入荷で増えた数量。 */
        int quantityReceived,
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
