package com.example.inventory.core.adapter.in.kafka;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * {@code wholesale.order.placed.v1} ペイロード。 Wholesale の {@code SalesOrderPlacedEvent} と項目構造を一致させる。
 *
 * <p>{@code partnerCode} と {@code requestedDeliveryDate} は Inventory Core の引当処理では使用しないが、
 * 同一スキーマで読めるようフィールドは保持する({@code @JsonIgnoreProperties(ignoreUnknown = true)} で 将来の追加項目を許容)。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WholesaleOrderPlacedMessage(
        long aggregateId,
        String code,
        String partnerCode,
        String currency,
        BigDecimal totalAmount,
        List<Line> items,
        LocalDate requestedDeliveryDate,
        Instant occurredAt) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Line(
            int lineNo, String skuCode, String locationId, int quantity, BigDecimal unitPrice) {}
}
