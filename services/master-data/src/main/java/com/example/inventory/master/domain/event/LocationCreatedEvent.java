package com.example.inventory.master.domain.event;

import java.time.Instant;

import com.example.inventory.commons.event.DomainEvent;

/**
 * Location マスタ新規作成イベント。{@code master.location.v1} トピックへ Outbox 経由で発行される。
 *
 * <p>購読者: 業態系サービス(WMS / 注文)、Integration Hub(WMS 同期)、Analytics(拠点ディメンション更新)。
 *
 * <p>post-state を含む(変更後の version 等)。下流が単一イベントから現在状態を再構築できる。
 */
public record LocationCreatedEvent(
        long aggregateId,
        String code,
        String name,
        String addressLine,
        String locationType,
        long versionAfter,
        Instant occurredAt)
        implements DomainEvent {

    public static final String TOPIC = "master.location.v1";
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
