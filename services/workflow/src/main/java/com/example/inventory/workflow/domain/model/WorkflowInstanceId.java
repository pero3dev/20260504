package com.example.inventory.workflow.domain.model;

/** WorkflowInstance 集約のサロゲート ID。Snowflake bigint(ADR-0011)。 */
public record WorkflowInstanceId(long value) {

    public WorkflowInstanceId {
        if (value <= 0) throw new IllegalArgumentException("WorkflowInstanceId は正の値");
    }
}
