package com.example.inventory.manufacturing.adapter.in.kafka;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** {@code manufacturing.completion.failed.v1} ペイロード。 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CompletionFailedMessage(
        long aggregateId,
        String workOrderCode,
        String errorCode,
        String reason,
        String productSkuCode,
        String locationId,
        int plannedQuantity,
        Instant occurredAt) {}
