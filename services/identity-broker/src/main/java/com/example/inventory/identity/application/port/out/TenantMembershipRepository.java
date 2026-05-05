package com.example.inventory.identity.application.port.out;

import java.util.List;
import java.util.Optional;

import com.example.inventory.commons.tenant.TenantId;
import com.example.inventory.identity.domain.model.TenantMembership;
import com.example.inventory.identity.domain.model.UserId;

/** ユーザーのテナントメンバーシップ参照ポート。 */
public interface TenantMembershipRepository {

    List<TenantMembership> findByUserId(UserId userId);

    Optional<TenantMembership> findByUserAndTenant(UserId userId, TenantId tenantId);
}
