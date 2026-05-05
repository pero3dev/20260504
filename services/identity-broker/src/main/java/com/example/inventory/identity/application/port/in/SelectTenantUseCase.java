package com.example.inventory.identity.application.port.in;

import com.example.inventory.commons.audit.AuditMask;

/** セッショントークン + tenantId → テナントスコープのアクセストークン。 */
public interface SelectTenantUseCase {

    Result selectTenant(Command command);

    /**
     * テナント選択コマンド。{@code sessionToken} は JWT 文字列のため {@link AuditMask} で audit
     * ペイロードからマスクする(他者がトークン値を知ると セッションを再利用できてしまうため)。
     */
    record Command(@AuditMask String sessionToken, String tenantId) {
        public Command {
            if (sessionToken == null || sessionToken.isBlank()) {
                throw new IllegalArgumentException("sessionToken is required");
            }
            if (tenantId == null || tenantId.isBlank()) {
                throw new IllegalArgumentException("tenantId is required");
            }
        }
    }

    record Result(String accessToken, long expiresInSeconds) {}
}
