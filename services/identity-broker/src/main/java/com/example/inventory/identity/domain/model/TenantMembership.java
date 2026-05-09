package com.example.inventory.identity.domain.model;

import java.util.Collections;
import java.util.List;

import com.example.inventory.commons.tenant.TenantId;

/**
 * ユーザーがテナントに対して持つメンバーシップ。
 *
 * <p>1 ユーザー × 1 テナントごとに 1 レコード。ロールと、データスコープ (アクセス可能な拠点 / 取引先)を保持する。これらは JWT 発行時に {@code roles} /
 * {@code scopes} クレームに展開される(ADR-0007)。
 *
 * <p>ADR-0022 phase 5a で {@code tenantLocale}(テナント運用言語、 BCP47 風)を denormalize 保持。 SoR は {@code
 * tenants.locale}、 本 record は JWT 発行 hot path で 1 row fetch で 取れるよう同 row に乗せる。
 */
public record TenantMembership(
        UserId userId,
        TenantId tenantId,
        String tenantDisplayName,
        String tenantLocale,
        List<RoleName> roles,
        List<String> locationScopes,
        List<String> partnerScopes) {

    public TenantMembership {
        if (userId == null) {
            throw new IllegalArgumentException("userId は必須");
        }
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId は必須");
        }
        if (tenantLocale == null || tenantLocale.isBlank()) {
            tenantLocale = "ja";
        }
        roles = roles == null ? List.of() : Collections.unmodifiableList(roles);
        locationScopes =
                locationScopes == null ? List.of() : Collections.unmodifiableList(locationScopes);
        partnerScopes =
                partnerScopes == null ? List.of() : Collections.unmodifiableList(partnerScopes);
    }

    /** 旧 6 引数 constructor 互換。 既存呼出点(test 等)を破壊しないために残す。 */
    public TenantMembership(
            UserId userId,
            TenantId tenantId,
            String tenantDisplayName,
            List<RoleName> roles,
            List<String> locationScopes,
            List<String> partnerScopes) {
        this(userId, tenantId, tenantDisplayName, "ja", roles, locationScopes, partnerScopes);
    }

    public List<String> roleNames() {
        return roles.stream().map(RoleName::value).toList();
    }
}
