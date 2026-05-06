package com.example.inventory.core.adapter.in.kafka;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * {@code wholesale.order.shipped.v1} ペイロード。 Wholesale 側 {@code SalesOrderShippedEvent} と項目構造を一致させる。
 *
 * <p>{@code partnerCode} と {@code shippedAt} は Inventory Core の出荷反映処理では使用しないが、
 * 同一スキーマで読めるようフィールドは保持する ({@code @JsonIgnoreProperties(ignoreUnknown = true)} で 将来の追加項目を許容)。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SalesOrderShippedMessage(
        long aggregateId,
        String code,
        String partnerCode,
        List<Line> items,
        Instant shippedAt,
        Instant occurredAt) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Line(int lineNo, String skuCode, String locationId, int quantity) {}
}
