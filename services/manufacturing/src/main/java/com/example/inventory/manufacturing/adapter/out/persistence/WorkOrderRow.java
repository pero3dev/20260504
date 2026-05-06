package com.example.inventory.manufacturing.adapter.out.persistence;

import java.time.Instant;
import java.time.LocalDate;

public record WorkOrderRow(
        long id,
        String code,
        String productSkuCode,
        String locationId,
        int plannedQuantity,
        String status,
        LocalDate plannedStartDate,
        long version,
        Instant placedAt,
        Instant releasedAt) {}
