package com.example.inventory.manufacturing.domain.model;

/** WorkOrder 集約のサロゲート ID。Snowflake bigint(ADR-0011)。 */
public record WorkOrderId(long value) {

    public WorkOrderId {
        if (value <= 0) {
            throw new IllegalArgumentException("WorkOrderId は正の値である必要があります(指定値: " + value + ")");
        }
    }
}
