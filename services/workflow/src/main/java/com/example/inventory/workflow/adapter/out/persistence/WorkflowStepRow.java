package com.example.inventory.workflow.adapter.out.persistence;

import java.time.Instant;

public record WorkflowStepRow(
        long instanceId,
        int stepNo,
        String name,
        String status,
        Instant startedAt,
        Instant completedAt,
        String error) {}
