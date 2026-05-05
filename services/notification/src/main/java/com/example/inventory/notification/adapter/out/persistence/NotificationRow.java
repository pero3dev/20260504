package com.example.inventory.notification.adapter.out.persistence;

import java.time.Instant;

public record NotificationRow(
        long id,
        String tenantId,
        String channel,
        String recipient,
        String subject,
        String body,
        String status,
        String errorMessage,
        String triggeredBy,
        Long triggeredEventId,
        Instant occurredAt) {}
