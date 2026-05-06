package com.example.inventory.core.domain.event;

import java.time.Instant;

import com.example.inventory.commons.event.DomainEvent;

/**
 * Manufacturing 製造指図リリースに伴う部品消費が失敗したことを通知する補償イベント。 {@code manufacturing.consumption.failed.v1}。
 *
 * <p>業態ごとに補償トピックを分離する方針(ADR で別途整理予定)。Wholesale 用 {@link WholesaleReservationFailedEvent}
 * と構造的には類似だが、Manufacturing は「1 指図に複数構成要素」を 持つ all-or-nothing 消費なので、失敗時には「どの構成要素で失敗したか」を {@code
 * failedComponentSkuCode} で示す。
 *
 * <p>主な発生原因:
 *
 * <ul>
 *   <li>{@code ERR_INVENTORY_INSUFFICIENT}(在庫不足)
 *   <li>{@code ERR_UNKNOWN_SKU}(構成 SKU 投影未到達 / 未登録)
 *   <li>{@code ERR_INVENTORY_NOT_FOUND_FOR_ORDER}((sku, location) の在庫レコード未作成)
 * </ul>
 *
 * <p>購読者: Manufacturing(WorkOrder を CANCELLED に)、Notification(製造担当通知)、Audit(監査)。
 */
public record WorkOrderConsumptionFailedEvent(
        long aggregateId,
        String workOrderCode,
        String errorCode,
        String reason,
        String failedComponentSkuCode,
        String failedLocationId,
        Instant occurredAt)
        implements DomainEvent {

    public static final String TOPIC = "manufacturing.consumption.failed.v1";
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
