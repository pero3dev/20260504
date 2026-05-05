package com.example.inventory.retail.adapter.in.kafka;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** {@code inventory.reservation.failed.v1} ペイロード。 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReservationFailedMessage(
        long aggregateId,
        String orderCode,
        String errorCode,
        String reason,
        String failedSkuCode,
        String failedLocationId,
        Instant occurredAt) {}
