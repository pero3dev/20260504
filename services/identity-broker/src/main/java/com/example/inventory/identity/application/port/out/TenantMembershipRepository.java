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

    /**
     * 該当 (userId, tenantId) の membership 行を物理削除する。
     *
     * @return 削除された行数(0 = 該当無し、 1 = 削除完了)。 caller は 0 を 404 に変換する
     */
    int delete(UserId userId, TenantId tenantId);

    /**
     * 指定 tenant に membership を持つ全 user の ID を返す。 tenant deactivate 時に対象 tenant 内ユーザの token を一括
     * revoke するために使う(ADR-0023 fanout)。 重複は (user_id, tenant_id) の UNIQUE 制約で原理的に発生しない。
     */
    List<UserId> findUserIdsByTenant(TenantId tenantId);
}
