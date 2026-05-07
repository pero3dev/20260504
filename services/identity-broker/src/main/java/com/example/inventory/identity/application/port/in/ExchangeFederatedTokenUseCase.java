package com.example.inventory.identity.application.port.in;

import java.util.List;

import com.example.inventory.commons.audit.AuditMask;
import com.example.inventory.identity.domain.model.TenantMembership;

/**
 * 外部 IdP(Cognito 等)が発行した access token を Identity Broker のセッショントークンに交換する。
 *
 * <p>SAML federation flow:
 *
 * <ol>
 *   <li>web app が Cognito Hosted UI へリダイレクト → ユーザは社内 IdP で認証
 *   <li>Cognito が code を返却 → web app が PKCE 経由で Cognito access token を取得
 *   <li>web app が本 endpoint を叩く → Identity Broker が token を検証し、 内部 User にマップ → IB セッショントークン +
 *       accessibleTenants[] を返す
 *   <li>web app が `POST /v1/auth/tenant-sessions` でテナント選択 → tenant-scoped JWT
 * </ol>
 *
 * 本 phase では JIT provisioning は実装しない。 列挙攻撃対策のため、 token 不正 / subject 未 provision / 内部 User 不在は全て
 * {@link AuthenticationFailedException}(401)に丸め込む (audit log には verifier 側で詳細を残す)。 SAML attribute →
 * User 同期は別 batch / SCIM で 実施する想定。
 */
public interface ExchangeFederatedTokenUseCase {

    Result exchange(Command command);

    /** 外部 IdP の access token(JWT)を直接渡す。 token 自体は機密情報なので {@link AuditMask} で audit ペイロードからマスク。 */
    record Command(@AuditMask String providerAccessToken) {
        public Command {
            if (providerAccessToken == null || providerAccessToken.isBlank()) {
                throw new IllegalArgumentException("providerAccessToken is required");
            }
        }
    }

    record Result(
            String sessionToken, long expiresInSeconds, List<TenantMembership> accessibleTenants) {}
}
