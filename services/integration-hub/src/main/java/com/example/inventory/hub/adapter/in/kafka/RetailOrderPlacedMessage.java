package com.example.inventory.hub.adapter.in.kafka;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * {@code retail.order.placed.v1} の payload。Retail/EC 側の {@code OrderPlacedEvent} と項目構造を一致させる。
 *
 * <p>{@code @JsonIgnoreProperties(ignoreUnknown = true)} で将来の追加項目を吸収。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RetailOrderPlacedMessage(
        long aggregateId,
        String code,
        String customerEmail,
        String currency,
        BigDecimal totalAmount,
        List<Line> items,
        Instant occurredAt) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Line(
            int lineNo, String skuCode, String locationId, int quantity, BigDecimal unitPrice) {}
}
