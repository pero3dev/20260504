package com.example.inventory.master.domain.model;

/** Partner 集約のサロゲート ID。Snowflake bigint(ADR-0011)。 */
public record PartnerId(long value) {

    public PartnerId {
        if (value <= 0) {
            throw new IllegalArgumentException("PartnerId は正の値である必要があります(指定値: " + value + ")");
        }
    }
}
