package com.example.inventory.core.domain.event;

import java.time.Instant;

import com.example.inventory.commons.event.DomainEvent;

/**
 * Inventory 集約の {@code release()} 成功時に発行される。{@code inventory.movement.v1} トピックへ Outbox 経由で発行。
 *
 * <p>引当済み(reserved)を available に戻した事実を表す。注文キャンセル時の補償として使われる。 {@code reserved} は減少し {@code
 * available} は同量増える(系外への出荷とは逆方向)。
 *
 * <p>{@link InventoryShippedEvent} とは {@code available} の挙動が反対(release は increase / ship は不変)。
 */
public record InventoryReleasedEvent(
        long aggregateId,
        String skuId,
        String locationId,
        /** この release で reserved から戻した数量。 */
        int quantityReleased,
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
