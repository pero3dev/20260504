package com.example.inventory.core.domain.event;

import java.time.Instant;

import com.example.inventory.commons.event.DomainEvent;

/**
 * Wholesale 受注の在庫引当が失敗したことを通知する補償イベント。 {@code wholesale.reservation.failed.v1}。
 *
 * <p>業態ごとに補償トピックを分離しているのは Saga 境界を業態ごとに独立させるため (リトライ戦略 / 購読 group_id / 監査ディメンションを業態ごとに別管理できる)。
 * Retail/EC 用の {@link OrderReservationFailedEvent} と構造は同じだが別トピック。
 *
 * <p>主な発生原因(Retail/EC と共通):
 *
 * <ul>
 *   <li>{@code ERR_INVENTORY_INSUFFICIENT}(在庫不足)
 *   <li>{@code ERR_UNKNOWN_SKU}(SKU 投影未到達 / 未登録)
 *   <li>{@code ERR_INVENTORY_NOT_FOUND_FOR_ORDER}((sku, location) の在庫レコード未作成)
 * </ul>
 *
 * <p>購読者: Wholesale(受注を CANCELLED に)、Notification(取引先担当者通知)、Audit(監査)。
 */
public record WholesaleReservationFailedEvent(
        long aggregateId,
        String orderCode,
        String errorCode,
        String reason,
        String failedSkuCode,
        String failedLocationId,
        Instant occurredAt)
        implements DomainEvent {

    public static final String TOPIC = "wholesale.reservation.failed.v1";
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
