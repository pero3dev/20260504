package com.example.inventory.tpl.domain.model;

/** StockMovement 集約のサロゲート ID。Snowflake bigint(ADR-0011)。 */
public record StockMovementId(long value) {

    public StockMovementId {
        if (value <= 0) {
            throw new IllegalArgumentException(
                    "StockMovementId は正の値である必要があります(指定値: " + value + ")");
        }
    }
}
