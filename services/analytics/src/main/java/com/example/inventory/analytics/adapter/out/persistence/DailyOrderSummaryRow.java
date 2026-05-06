package com.example.inventory.analytics.adapter.out.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record DailyOrderSummaryRow(
        String tenantId,
        String businessContext,
        LocalDate summaryDate,
        String currency,
        long orderCount,
        BigDecimal totalAmount,
        Instant lastEventAt) {}
