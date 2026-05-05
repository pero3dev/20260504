package com.example.inventory.master.domain.event;

import java.time.Instant;

import com.example.inventory.commons.event.DomainEvent;

/**
 * Partner マスタ新規作成イベント。{@code master.partner.v1} トピックへ Outbox 経由で発行される。
 *
 * <p>購読者: 業態系サービス(Wholesale / 3PL)、Integration Hub(EDI 同期)、Analytics(取引先ディメンション更新)。
 *
 * <p>{@code partnerType} は CUSTOMER / SUPPLIER / CARRIER 等の業務分類(自由文字列、enum 化はしない)。
 */
public record PartnerCreatedEvent(
        long aggregateId,
        String code,
        String name,
        String partnerType,
        String contactEmail,
        long versionAfter,
        Instant occurredAt)
        implements DomainEvent {

    public static final String TOPIC = "master.partner.v1";
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
