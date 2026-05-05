package com.example.inventory.core.adapter.out.persistence;

/** {@code sku_registry} テーブルのフラット行表現。 */
public record SkuRegistryRow(
        String code, long aggregateId, String name, String unitOfMeasure, long version) {}
