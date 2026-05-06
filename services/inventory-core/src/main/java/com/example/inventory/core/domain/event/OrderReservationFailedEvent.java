package com.example.inventory.core.domain.event;

import java.time.Instant;

import com.example.inventory.commons.event.DomainEvent;

/**
 * Retail/EC 注文の在庫引当が失敗したことを通知する補償イベント。{@code retail.reservation.failed.v1}。
 *
 * <p>業態ごとに補償トピックを分離する方針(ADR-0016)に従う。Wholesale 用 {@code WholesaleReservationFailedEvent}
 * と構造的には類似だが、別トピック。
 *
 * <p>主な発生原因:
 *
 * <ul>
 *   <li>{@code ERR_INVENTORY_INSUFFICIENT}(在庫不足)
 *   <li>{@code ERR_UNKNOWN_SKU}(SKU 投影未到達 / 未登録)
 *   <li>{@code ERR_INVENTORY_NOT_FOUND_FOR_ORDER}((sku, location) の在庫レコード未作成)
 * </ul>
 *
 * <p>購読者: Retail/EC(注文を CANCELLED に)、Notification(顧客通知)、Audit(監査)。
 *
 * <p>{@code aggregateId} は注文 ID(retail-ec の Order.id)。Inventory Core の Inventory.id ではない点に注意。
 * 補償の宛先(retail-ec)が自身の集約として識別できるよう Order の id を入れている。
 */
public record OrderReservationFailedEvent(
        long aggregateId,
        String orderCode,
        String errorCode,
        String reason,
        String failedSkuCode,
        String failedLocationId,
        Instant occurredAt)
        implements DomainEvent {

    public static final String TOPIC = "retail.reservation.failed.v1";
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
