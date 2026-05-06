package com.example.inventory.wholesale.domain.event;

import java.time.Instant;
import java.util.List;

import com.example.inventory.commons.event.DomainEvent;

/**
 * 受注キャンセル確定イベント。{@code wholesale.order.cancelled.v1} トピックへ Outbox 経由で発行される。
 *
 * <p>業務側で取消が確定した時点で発行 — Inventory Core はこれを受けて該当の reserved を解放(release)する。 これにより 「注文キャンセル後に
 * reserved が宙ぶらりんで戻らない」整合性破綻を解消する。
 *
 * <p>引当補償({@code wholesale.reservation.failed.v1})とは独立: 補償は「Reserve に失敗 → Order を取消」、 本イベントは「業務側で取消
 * → Reserved を解放」と方向が逆。
 */
public record SalesOrderCancelledEvent(
        long aggregateId,
        String code,
        String partnerCode,
        List<Line> items,
        Instant cancelledAt,
        Instant occurredAt)
        implements DomainEvent {

    public static final String TOPIC = "wholesale.order.cancelled.v1";
    public static final String SCHEMA_VERSION = "1.0";

    /** 明細(Reserve 時と同じ識別子で release できるよう、line_no/sku/location/quantity を保持)。 */
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
