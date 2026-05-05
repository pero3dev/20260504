package com.example.inventory.core.domain.model;

/** {@link Inventory} 集約のID。Snowflake 生成の bigint(ADR-0011)。 */
public record InventoryId(long value) {

    public InventoryId {
        if (value <= 0) {
            throw new IllegalArgumentException("InventoryId は正の値である必要があります(指定値: " + value + ")");
        }
    }
}
