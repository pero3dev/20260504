package com.example.inventory.wholesale.adapter.out.persistence;

import java.math.BigDecimal;

public record PartnerPriceRow(
        String partnerCode,
        String skuCode,
        String priceTier,
        BigDecimal unitPrice,
        String currency) {}
