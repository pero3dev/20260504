package com.example.inventory.identity.config;

/**
 * SAML JIT(Just-In-Time)provisioning 設定。
 *
 * <p>外部 IdP(Cognito 等)で認証成功した subject が内部 User に未存在の時、 401 で蹴る代わりに default tenant + role で User +
 * TenantMembership を自動 provision するかどうか。
 *
 * <p>{@code platform.identity.federation.jit.*} で設定:
 *
 * <ul>
 *   <li>{@code enabled} — JIT を有効化(default false、 列挙攻撃対策と合わせ運用 ready 後に明示 ON)
 *   <li>{@code default-tenant-id} — JIT 作成時に貼る初期 TenantMembership のテナント
 *   <li>{@code default-role} — 同 TenantMembership のロール(default {@code VIEWER})
 * </ul>
 *
 * <p>本 phase の simplification: SAML attribute による tenant 振り分けは行わない。 default 1 tenant に全員入れる構成のみ。
 * マルチテナント振り分けは将来 SCIM 連携 / 別 SAML attribute mapping で実装する想定。
 */
public record FederationJitProperties(boolean enabled, String defaultTenantId, String defaultRole) {

    public static final String DEFAULT_ROLE = "VIEWER";

    public FederationJitProperties {
        if (defaultRole == null || defaultRole.isBlank()) {
            defaultRole = DEFAULT_ROLE;
        }
    }
}
