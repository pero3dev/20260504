package com.example.inventory.wholesale.adapter.out.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record SalesOrderRow(
        long id,
        String code,
        String partnerCode,
        String status,
        BigDecimal totalAmount,
        String currency,
        LocalDate requestedDeliveryDate,
        long version,
        Instant placedAt,
        Instant shippedAt) {}
