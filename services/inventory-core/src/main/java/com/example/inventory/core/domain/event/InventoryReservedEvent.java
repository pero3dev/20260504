package com.example.inventory.core.domain.event;

import java.time.Instant;

import com.example.inventory.commons.event.DomainEvent;

/**
 * Inventory 集約の {@code reserve()} 成功時に発行される。 {@code inventory.movement.v1} トピックへ Outbox 経由で発行される。
 *
 * <p>購読者: Inventory Read Model(Redis投影更新)、Audit(監査記録)、 Integration Hub(EC/ERPなど外部下流連携)。
 *
 * <p>イベントは「事実」として post-state を含む(Inventory Read Model が単一イベントから 投影を完全再構築できるよう、状態を遡って計算する必要を無くすため)。
 *
 * <p>eventId は持たない。OutboxRecord と Kafka ヘッダにて publisher が採番する。
 */
public record InventoryReservedEvent(
        long aggregateId,
        String skuId,
        String locationId,
        long reservationId,
        /** この引当で動かした数量(差分)。 */
        int quantityReserved,
        /** 引当後の利用可能数量(post-state)。 */
        int availableAfter,
        /** 引当後の引当済数量(post-state)。 */
        int reservedAfter,
        /** 引当後の集約 version(post-state)。Read Model の冪等性チェックに使う。 */
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
