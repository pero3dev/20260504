package com.example.inventory.manufacturing.adapter.out.persistence;

public record WorkOrderComponentRow(
        long workOrderId, int lineNo, String componentSkuCode, int quantityPerUnit) {}
