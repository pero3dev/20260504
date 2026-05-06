package com.example.inventory.manufacturing.domain.event;

import java.time.Instant;

import com.example.inventory.commons.event.DomainEvent;

/**
 * 製造指図完了イベント。{@code manufacturing.work_order.completed.v1} トピックへ Outbox 経由で発行される。
 *
 * <p>購読者: Inventory Core(完成品 SKU の {@code Inventory.receive} 呼出 = 完成品入庫)、Notification(製造完了通知)、
 * Analytics(製造リードタイム / 良品率の集計、完成品トレーサビリティ)。
 *
 * <p>ADR-0017 follow-up tasks に対応。{@code WorkOrder.release} で部品が消費され、本イベント受信で完成品在庫が増える
 * ことで、Manufacturing のフローが「部品消費 → 完成品 INBOUND」まで閉じる。
 *
 * <p>MVP は実績数量訂正なし({@code completedQuantity = plannedQuantity})。歩留まりや過剰生産の表現は将来拡張(別 ADR で議論)。
 */
public record WorkOrderCompletedEvent(
        long aggregateId,
        String code,
        String productSkuCode,
        String locationId,
        int completedQuantity,
        Instant completedAt,
        Instant occurredAt)
        implements DomainEvent {

    public static final String TOPIC = "manufacturing.work_order.completed.v1";
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
