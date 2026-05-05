package com.example.inventory.tpl.adapter.out.persistence;

import java.time.Instant;

public record StockMovementRow(
        long id,
        String code,
        String partnerCode,
        String skuCode,
        String locationId,
        String movementType,
        int quantity,
        String status,
        String referenceCode,
        long version,
        Instant plannedAt) {}
