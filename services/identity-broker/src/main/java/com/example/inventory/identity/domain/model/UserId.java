package com.example.inventory.identity.domain.model;

/** ユーザーID。Snowflake bigint(ADR-0011)。 */
public record UserId(long value) {

    public UserId {
        if (value <= 0) {
            throw new IllegalArgumentException("UserId は正の値である必要があります(指定値: " + value + ")");
        }
    }
}
