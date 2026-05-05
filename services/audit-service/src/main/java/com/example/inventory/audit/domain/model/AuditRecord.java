package com.example.inventory.audit.domain.model;

import java.time.Instant;

import com.example.inventory.commons.tenant.TenantId;

/**
 * 永続化される監査レコード。SHA-256 ハッシュチェーンで連鎖し、改竄を検出可能にする(ADR-0008)。
 *
 * <p>{@code prevHash} は同テナントの直前レコードの {@code hash}(初回は {@link HashHex#INITIAL})。 {@code hash} は
 * {@code SHA-256(prevHash + canonical(record))} で計算される。
 */
public record AuditRecord(
        TenantId tenantId,
        long sequence,
        long eventId,
        String action,
        String targetType,
        String targetId,
        String operatorUserId,
        String operatorTenantId,
        AuditOutcome outcome,
        String errorCode,
        boolean readOnly,
        String payloadJson,
        Instant occurredAt,
        HashHex prevHash,
        HashHex hash) {}
