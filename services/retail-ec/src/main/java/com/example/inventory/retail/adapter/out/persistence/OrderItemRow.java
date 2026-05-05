package com.example.inventory.retail.adapter.out.persistence;

import java.math.BigDecimal;

public record OrderItemRow(
        long orderId, int lineNo, String skuCode, int quantity, BigDecimal unitPrice) {}
