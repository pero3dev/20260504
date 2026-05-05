package com.example.inventory.core.adapter.in.kafka;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** {@code retail.order.placed.v1} ペイロード。Retail/EC の OrderPlacedEvent と項目構造を一致させる。 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderPlacedMessage(
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
