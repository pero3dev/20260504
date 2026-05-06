package com.example.inventory.manufacturing.adapter.out.persistence;

public record BomComponentRow(
        String productSkuCode, String componentSkuCode, int quantityPerUnit) {}
