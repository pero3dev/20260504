package com.example.inventory.master.domain.model;

/** Location 集約のサロゲート ID。Snowflake bigint(ADR-0011)。 */
public record LocationId(long value) {

    public LocationId {
        if (value <= 0) {
            throw new IllegalArgumentException("LocationId は正の値である必要があります(指定値: " + value + ")");
        }
    }
}
