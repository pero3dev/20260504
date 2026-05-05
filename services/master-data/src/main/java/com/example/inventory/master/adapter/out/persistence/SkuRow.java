package com.example.inventory.master.adapter.out.persistence;

public record SkuRow(
        long id,
        String code,
        String name,
        String description,
        String unitOfMeasure,
        long version) {}
