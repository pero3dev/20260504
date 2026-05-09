package com.example.inventory.identity.adapter.out.persistence;

public record TenantMembershipRow(
        long userId,
        String tenantId,
        String tenantDisplayName,
        String tenantLocale,
        /** JSON 配列文字列(ロール名)。 */
        String rolesJson,
        /** JSON 配列文字列(拠点ID)。 */
        String locationScopesJson,
        /** JSON 配列文字列(取引先ID)。 */
        String partnerScopesJson) {}
