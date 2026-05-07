package com.example.inventory.identity.adapter.out.persistence;

import java.time.Instant;

/** {@code tenants} テーブル行。 MyBatis のリザルトマップで使用。 */
public record TenantRow(
        String tenantId,
        String displayName,
        String status,
        Instant createdAt,
        Instant deactivatedAt) {}
