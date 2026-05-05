package com.example.inventory.core.domain.model;

/** 在庫予約(引当)の識別子。Snowflake 生成の bigint。 */
public record ReservationId(long value) {

    public ReservationId {
        if (value <= 0) {
            throw new IllegalArgumentException("ReservationId は正の値である必要があります(指定値: " + value + ")");
        }
    }
}
