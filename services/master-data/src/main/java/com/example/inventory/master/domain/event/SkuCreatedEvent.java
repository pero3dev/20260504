package com.example.inventory.master.domain.event;

import java.time.Instant;

import com.example.inventory.commons.event.DomainEvent;

/**
 * SKU マスタ新規作成イベント。{@code master.product.v1} トピックへ Outbox 経由で発行される。
 *
 * <p>購読者: Inventory Core(マスタ参照キャッシュ更新)、Integration Hub(EC/ERP への同期)、 Analytics(マスタディメンション更新)。
 *
 * <p>post-state を含む(変更後の version 等)。下流が単一イベントから現在状態を再構築できる。
 */
public record SkuCreatedEvent(
        long aggregateId,
        String code,
        String name,
        String description,
        String unitOfMeasure,
        long versionAfter,
        Instant occurredAt)
        implements DomainEvent {

    public static final String TOPIC = "master.product.v1";
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
