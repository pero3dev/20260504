package com.example.inventory.master.domain.model;

/** SKU 集約のサロゲート ID。Snowflake bigint(ADR-0011)。 */
public record SkuId(long value) {

    public SkuId {
        if (value <= 0) {
            throw new IllegalArgumentException("SkuId は正の値である必要があります(指定値: " + value + ")");
        }
    }
}
