package com.example.inventory.audit.adapter.out.persistence;

import java.time.Instant;

/** {@code audit_record} テーブルの行表現。 */
public record AuditRecordRow(
        String tenantId,
        long sequence,
        long eventId,
        String action,
        String targetType,
        String targetId,
        String operatorUserId,
        String operatorTenantId,
        String outcome,
        String errorCode,
        boolean readOnly,
        String payloadJson,
        Instant occurredAt,
        String prevHash,
        String hash) {}
