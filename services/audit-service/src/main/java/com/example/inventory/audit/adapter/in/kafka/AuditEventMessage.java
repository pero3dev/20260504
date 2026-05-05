package com.example.inventory.audit.adapter.in.kafka;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * {@code audit.log.v1} ペイロード型。commons-audit の AuditEvent の JSON 表現と一対一対応する。
 *
 * <p>意図的に commons-audit からインポートしていない:audit-service と producer は別ドメイン。 接点はトピックスキーマ(将来 Glue Schema
 * Registry に集約)。
 *
 * <p>{@code @JsonIgnoreProperties} で未知フィールドを許容する(将来追加されたフィールドを安全にスキップ)。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AuditEventMessage(
        String action,
        String targetType,
        String targetId,
        String operatorUserId,
        String operatorTenantId,
        String outcome,
        String errorCode,
        Boolean read,
        String inputJson,
        Instant occurredAt) {}
