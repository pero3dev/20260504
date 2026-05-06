package com.example.inventory.core.adapter.in.kafka;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 注文キャンセルイベントの payload(retail / wholesale 共通形式)。 Inventory Core から見ると customerEmail / partnerCode
 * 等の業態固有メタは不要なので、 {@code @JsonIgnoreProperties(ignoreUnknown = true)} で吸収する。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderCancelledMessage(
        long aggregateId, String code, List<Line> items, Instant cancelledAt, Instant occurredAt) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Line(int lineNo, String skuCode, String locationId, int quantity) {}
}
