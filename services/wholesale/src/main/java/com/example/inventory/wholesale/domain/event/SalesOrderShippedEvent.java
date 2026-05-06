package com.example.inventory.wholesale.domain.event;

import java.time.Instant;
import java.util.List;

import com.example.inventory.commons.event.DomainEvent;

/**
 * 受注出荷確定イベント。{@code wholesale.order.shipped.v1} トピックへ Outbox 経由で発行される。
 *
 * <p>購読者: Inventory Core(reserved → 系外、{@code Inventory.ship} 呼出)、Notification(取引先担当者への出荷通知)、
 * Integration Hub(EDI 出荷確認)、Analytics(出荷リードタイム / KPI 集計)。
 *
 * <p>ADR-0017 に従い、Wholesale 受注は Reserve のみで Inventory Core に渡し、 出荷確定時に本イベントで ship を呼び切る 2
 * 段構成で受注〜出荷フローを閉じる。
 */
public record SalesOrderShippedEvent(
        long aggregateId,
        String code,
        String partnerCode,
        List<Line> items,
        Instant shippedAt,
        Instant occurredAt)
        implements DomainEvent {

    public static final String TOPIC = "wholesale.order.shipped.v1";
    public static final String SCHEMA_VERSION = "1.0";

    /** 明細(イベント payload 用フラット化、出荷量 = 受注量と同じ前提で MVP は数量訂正なし)。 */
    public record Line(int lineNo, String skuCode, String locationId, int quantity) {}

    @Override
    public String topic() {
        return TOPIC;
    }

    @Override
    public String schemaVersion() {
        return SCHEMA_VERSION;
    }
}
