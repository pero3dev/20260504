package com.example.inventory.wholesale.adapter.in.kafka;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** {@code wholesale.reservation.failed.v1} ペイロード。 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReservationFailedMessage(
        long aggregateId,
        String orderCode,
        String errorCode,
        String reason,
        String failedSkuCode,
        String failedLocationId,
        Instant occurredAt) {}
