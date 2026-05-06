package com.example.inventory.wholesale.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.example.inventory.commons.event.DomainEvent;

/**
 * 受注確定イベント。{@code wholesale.order.placed.v1} トピックへ Outbox 経由で発行される。
 *
 * <p>購読者: Inventory Core(在庫引当、D9 で配線予定)、Notification(取引先担当者への発注確認)、 Integration Hub(EDI / 取引先 ERP
 * 連携)、Analytics(取引先別売上ディメンション更新)。
 *
 * <p>明細は post-state を含めて 1 イベントで完結させる。Retail/EC の {@code retail.order.placed.v1} と スキーマ構造は近いが、
 * customerEmail の代わりに partnerCode を持ち、希望納期(任意)を含む点が異なる。
 */
public record SalesOrderPlacedEvent(
        long aggregateId,
        String code,
        String partnerCode,
        String currency,
        BigDecimal totalAmount,
        List<Line> items,
        LocalDate requestedDeliveryDate,
        Instant occurredAt)
        implements DomainEvent {

    public static final String TOPIC = "wholesale.order.placed.v1";
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
