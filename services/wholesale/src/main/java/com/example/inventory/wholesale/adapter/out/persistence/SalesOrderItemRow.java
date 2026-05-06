package com.example.inventory.wholesale.adapter.out.persistence;

import java.math.BigDecimal;

public record SalesOrderItemRow(
        long orderId,
        int lineNo,
        String skuCode,
        String locationId,
        int quantity,
        BigDecimal unitPrice) {}
