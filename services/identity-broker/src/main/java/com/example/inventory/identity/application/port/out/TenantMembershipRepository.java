package com.example.inventory.identity.application.port.out;

import java.util.List;
import java.util.Optional;

import com.example.inventory.commons.tenant.TenantId;
import com.example.inventory.identity.domain.model.TenantMembership;
import com.example.inventory.identity.domain.model.UserId;

/** ユーザーのテナントメンバーシップ参照 + 作成ポート。 */
public interface TenantMembershipRepository {

    List<TenantMembership> findByUserId(UserId userId);

    Optional<TenantMembership> findByUserAndTenant(UserId userId, TenantId tenantId);

    /**
     * 新規 TenantMembership を INSERT する。 SAML JIT provisioning 経路でのみ呼ぶ。 (user_id, tenant_id)
     * 一意制約に違反した場合は SQL 例外を上げる(caller が認証失敗に丸める)。
     */
    void add(TenantMembership membership);
}
