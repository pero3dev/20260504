package com.example.inventory.retail.domain.model;

/** Order 集約のサロゲート ID。Snowflake bigint(ADR-0011)。 */
public record OrderId(long value) {

    public OrderId {
        if (value <= 0) {
            throw new IllegalArgumentException("OrderId は正の値である必要があります(指定値: " + value + ")");
        }
    }
}
