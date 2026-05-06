package com.example.inventory.manufacturing.adapter.in.kafka;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** {@code manufacturing.consumption.failed.v1} ペイロード。 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ConsumptionFailedMessage(
        long aggregateId,
        String workOrderCode,
        String errorCode,
        String reason,
        String failedComponentSkuCode,
        String failedLocationId,
        Instant occurredAt) {}
