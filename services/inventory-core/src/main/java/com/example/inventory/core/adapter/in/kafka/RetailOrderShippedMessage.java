package com.example.inventory.core.adapter.in.kafka;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * {@code retail.order.shipped.v1} ペイロード。 Retail/EC 側 {@code OrderShippedEvent} と項目構造を一致させる。
 *
 * <p>{@code customerEmail} と {@code shippedAt} は Inventory Core の出荷反映処理では使用しないが、
 * 同一スキーマで読めるようフィールドは保持する ({@code @JsonIgnoreProperties(ignoreUnknown = true)} で 将来の追加項目を許容)。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RetailOrderShippedMessage(
        long aggregateId,
        String code,
        String customerEmail,
        List<Line> items,
        Instant shippedAt,
        Instant occurredAt) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Line(int lineNo, String skuCode, String locationId, int quantity) {}
}
