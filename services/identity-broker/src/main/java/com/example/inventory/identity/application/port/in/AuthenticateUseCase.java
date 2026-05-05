package com.example.inventory.identity.application.port.in;

import java.util.List;

import com.example.inventory.commons.audit.AuditMask;
import com.example.inventory.identity.domain.model.TenantMembership;

/** クレデンシャル認証 → セッショントークン + アクセス可能テナント一覧。 */
public interface AuthenticateUseCase {

    Result authenticate(Command command);

    /**
     * 認証コマンド。{@code password} は {@link AuditMask} が付いており、 audit ペイロードでは {@code "***"} に置換されて記録される
     * (通常のレスポンスやログには影響しない)。
     */
    record Command(String email, @AuditMask String password) {
        public Command {
            if (email == null || email.isBlank()) {
                throw new IllegalArgumentException("email is required");
            }
            if (password == null || password.isEmpty()) {
                throw new IllegalArgumentException("password is required");
            }
        }
    }

    record Result(
            String sessionToken, long expiresInSeconds, List<TenantMembership> accessibleTenants) {}
}
