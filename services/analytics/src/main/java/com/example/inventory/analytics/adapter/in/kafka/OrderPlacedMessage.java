package com.example.inventory.analytics.adapter.in.kafka;

import java.math.BigDecimal;
import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 注文確定イベントの payload(retail / wholesale で構造的に一致する部分のみ)。
 *
 * <p>Retail/EC は {@code customerEmail}、Wholesale は {@code partnerCode} と {@code
 * requestedDeliveryDate} を持つが、Analytics の集計には不要なので {@code @JsonIgnoreProperties(ignoreUnknown =
 * true)} で吸収する。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderPlacedMessage(
        long aggregateId,
        String code,
        String currency,
        BigDecimal totalAmount,
        Instant occurredAt) {}
