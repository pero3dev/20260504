package com.example.inventory.manufacturing.domain.event;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.example.inventory.commons.event.DomainEvent;

/**
 * 製造指図リリース(着手指示)イベント。{@code manufacturing.work_order.released.v1} トピックへ Outbox 経由で発行される。
 *
 * <p>購読者: Inventory Core(部品引当 = 構成要素分の在庫を引当・消費する、D10 で配線予定)、 Notification(製造担当への着手指示)、
 * Analytics(製造リードタイム算出、稼働率)。
 *
 * <p>BOM スナップショット {@code components} を payload に含めることで、購読側は BOM 改訂の影響を受けずに 当時の構成で動ける。
 */
public record WorkOrderReleasedEvent(
        long aggregateId,
        String code,
        String productSkuCode,
        String locationId,
        int plannedQuantity,
        List<Component> components,
        LocalDate plannedStartDate,
        Instant occurredAt)
        implements DomainEvent {

    public static final String TOPIC = "manufacturing.work_order.released.v1";
    public static final String SCHEMA_VERSION = "1.0";

    /**
     * 構成要素(payload 用フラット化)。{@code requiredQuantity = quantityPerUnit * plannedQuantity} のスナップショット。
     */
    public record Component(String componentSkuCode, int requiredQuantity) {}

    @Override
    public String topic() {
        return TOPIC;
    }

    @Override
    public String schemaVersion() {
        return SCHEMA_VERSION;
    }
}
