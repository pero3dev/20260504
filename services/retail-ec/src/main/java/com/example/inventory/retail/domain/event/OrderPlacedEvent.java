package com.example.inventory.retail.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.example.inventory.commons.event.DomainEvent;

/**
 * 注文確定イベント。{@code retail.order.placed.v1} トピックへ Outbox 経由で発行される。
 *
 * <p>購読者: Inventory Core(在庫引当)、Notification(顧客への確認メール)、 Integration Hub(EC ↔ ERP 連携)、Analytics
 * (注文ディメンション更新)。
 *
 * <p>明細は post-state を含めて 1 イベントで完結させる(下流が Order の現在状態を再構築できるよう)。
 */
public record OrderPlacedEvent(
        long aggregateId,
        String code,
        String customerEmail,
        String currency,
        BigDecimal totalAmount,
        List<Line> items,
        Instant occurredAt)
        implements DomainEvent {

    public static final String TOPIC = "retail.order.placed.v1";
    public static final String SCHEMA_VERSION = "1.0";

    /** 明細(イベント payload 用フラット化)。 */
    public record Line(
            int lineNo, String skuCode, String locationId, int quantity, BigDecimal unitPrice) {}

    @Override
    public String topic() {
        return TOPIC;
    }

    @Override
    public String schemaVersion() {
        return SCHEMA_VERSION;
    }
}
