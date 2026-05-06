package com.example.inventory.workflow.adapter.out.persistence;

import java.time.Instant;

public record WorkflowInstanceRow(
        long id,
        String tenantId,
        String definitionKey,
        String businessKey,
        String payloadJson,
        int currentStep,
        int totalSteps,
        String status,
        String error,
        long version,
        Instant startedAt,
        Instant completedAt) {}
