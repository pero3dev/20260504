package com.example.inventory.identity.domain.model;

import java.util.Collections;
import java.util.List;

import com.example.inventory.commons.tenant.TenantId;

/**
 * ユーザーがテナントに対して持つメンバーシップ。
 *
 * <p>1 ユーザー × 1 テナントごとに 1 レコード。ロールと、データスコープ (アクセス可能な拠点 / 取引先)を保持する。これらは JWT 発行時に {@code roles} /
 * {@code scopes} クレームに展開される(ADR-0007)。
 */
public record TenantMembership(
        UserId userId,
        TenantId tenantId,
        String tenantDisplayName,
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
        roles = roles == null ? List.of() : Collections.unmodifiableList(roles);
        locationScopes =
                locationScopes == null ? List.of() : Collections.unmodifiableList(locationScopes);
        partnerScopes =
                partnerScopes == null ? List.of() : Collections.unmodifiableList(partnerScopes);
    }

    public List<String> roleNames() {
        return roles.stream().map(RoleName::value).toList();
    }
}
