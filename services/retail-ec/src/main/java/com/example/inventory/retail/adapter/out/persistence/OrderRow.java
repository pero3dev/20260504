package com.example.inventory.retail.adapter.out.persistence;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderRow(
        long id,
        String code,
        String customerEmail,
        String status,
        BigDecimal totalAmount,
        String currency,
        long version,
        Instant placedAt) {}
