package com.example.inventory.analytics.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.example.inventory.commons.tenant.TenantId;

/**
 * テナント × 業態 × 日付 単位の注文集計値オブジェクト。
 *
 * <p>{@code orderCount} と {@code totalAmount} はその bucket の累計。 イベント受信ごとに UPSERT で値が加算される。
 */
public record DailyOrderSummary(
        TenantId tenantId,
        BusinessContext businessContext,
        LocalDate summaryDate,
        String currency,
        long orderCount,
        BigDecimal totalAmount,
        Instant lastEventAt) {

    public DailyOrderSummary {
        if (tenantId == null) throw new IllegalArgumentException("tenantId は必須");
        if (businessContext == null) throw new IllegalArgumentException("businessContext は必須");
        if (summaryDate == null) throw new IllegalArgumentException("summaryDate は必須");
        if (currency == null || currency.length() != 3)
            throw new IllegalArgumentException("currency は 3 文字 ISO 4217");
        if (orderCount < 0) throw new IllegalArgumentException("orderCount は非負");
        if (totalAmount == null || totalAmount.signum() < 0)
            throw new IllegalArgumentException("totalAmount は非負必須");
    }
}
