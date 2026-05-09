package com.example.inventory.identity.application.usecase;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.inventory.commons.audit.Auditable;
import com.example.inventory.commons.security.RevocationStore;
import com.example.inventory.commons.tenant.TenantId;
import com.example.inventory.identity.application.port.in.DeactivateTenantUseCase;
import com.example.inventory.identity.application.port.in.GetTenantUseCase;
import com.example.inventory.identity.application.port.in.RegisterTenantUseCase;
import com.example.inventory.identity.application.port.in.TenantAlreadyExistsException;
import com.example.inventory.identity.application.port.in.TenantNotFoundException;
import com.example.inventory.identity.application.port.in.TenantProtectedException;
import com.example.inventory.identity.application.port.out.TenantMembershipRepository;
import com.example.inventory.identity.application.port.out.TenantRepository;
import com.example.inventory.identity.domain.model.Tenant;
import com.example.inventory.identity.domain.model.UserId;

/**
 * Tenant lifecycle のユースケース集約(A5、 ADR-0003 follow-up)。
 *
 * <p>3 ポート(Register / Deactivate / Get)を 1 サービスで実装。 各 use case は独立 TX で、 Pool 方式の identity-broker
 * DB に直接書き込む。 business DB 側の schema 作成は別経路(infra/tenant-provisioning runbook)。
 *
 * <p>{@link Clock} を注入しテスト可能性を確保。
 *
 * <p><b>監査:</b> {@code /v1/admin/tenants/*} は J-SOX 上の重要統制点(テナント追加/削除/参照)のため、 4 メソッド全てに {@link
 * com.example.inventory.commons.audit.Auditable} を付与。 read-only な {@code get} / {@code listAll} も
 * {@code read = true} で監査(管理者の参照行為自体が統制対象)。
 *
 * <p><b>保護テナント:</b> {@value #PLATFORM_TENANT_ID} は SUPER_ADMIN provisioning 用の予約テナント (V4 migration
 * で seed)。 deactivate すると admin が完全にロックアウトされるため、 deactivate を {@link TenantProtectedException}
 * (409) で拒否する。
 */
@Service
public class TenantManagementService
        implements RegisterTenantUseCase, DeactivateTenantUseCase, GetTenantUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(TenantManagementService.class);

    /** SUPER_ADMIN provisioning 用の予約テナント ID。 V4 migration で seed され、 deactivate 不可。 */
    public static final String PLATFORM_TENANT_ID = "platform";

    /** ADR-0023 で定義した access token TTL に揃える revocation 登録の TTL。 */
    private static final Duration REVOCATION_TTL = Duration.ofMinutes(15);

    private final TenantRepository repository;
    private final TenantMembershipRepository membershipRepository;
    private final Clock clock;
    private final RevocationStore revocationStore;

    public TenantManagementService(
            TenantRepository repository,
            TenantMembershipRepository membershipRepository,
            Clock clock,
            RevocationStore revocationStore) {
        this.repository = repository;
        this.membershipRepository = membershipRepository;
        this.clock = clock;
        this.revocationStore = revocationStore;
    }

    @Override
    @Transactional
    @Auditable(
            action = "TENANT_REGISTER",
            targetType = "Tenant",
            targetIdExpression = "#command.tenantId")
    public Tenant register(Command command) {
        TenantId id = new TenantId(command.tenantId());
        Tenant tenant = Tenant.register(id, command.displayName(), clock.instant());
        try {
            repository.append(tenant);
            LOG.info(
                    "tenant 登録完了 tenantId={} displayName={}",
                    tenant.tenantId().value(),
                    tenant.displayName());
            return tenant;
        } catch (DuplicateKeyException e) {
            throw new TenantAlreadyExistsException(command.tenantId());
        }
    }

    @Override
    @Transactional
    @Auditable(
            action = "TENANT_DEACTIVATE",
            targetType = "Tenant",
            targetIdExpression = "#tenantId")
    public Tenant deactivate(String tenantId) {
        if (PLATFORM_TENANT_ID.equals(tenantId)) {
            // 列挙攻撃対策ではないので info で十分。 invariant 違反として運用が見られるよう残す。
            LOG.info("tenant deactivate 拒否(platform tenant 保護) tenantId={}", tenantId);
            throw new TenantProtectedException(tenantId);
        }
        TenantId id = new TenantId(tenantId);
        Tenant tenant =
                repository.findById(id).orElseThrow(() -> new TenantNotFoundException(tenantId));
        tenant.deactivate(clock.instant());
        repository.update(tenant);

        // ADR-0023 fanout: tenant DEACTIVATED 後も既発行 access token は signature/exp だけ見る stateless
        // 検証で TTL までアクセス継続を許す。 SelectTenantService は次回 token 発行を弾くが、 既発行分は別経路で
        // 即時無効化する必要がある。 当該 tenant に membership を持つ全 user を per-user revoke する。
        // (per-user 単位なので、 他テナントへの membership があるユーザは再 login で正常に新 token を取れる。)
        List<UserId> revokedUsers = membershipRepository.findUserIdsByTenant(id);
        for (UserId userId : revokedUsers) {
            revocationStore.revokeUser(userId.value(), REVOCATION_TTL);
        }
        LOG.info(
                "tenant 非活性化 tenantId={} status={} revoke 登録ユーザ数={}(ADR-0023)",
                tenant.tenantId().value(),
                tenant.status(),
                revokedUsers.size());
        return tenant;
    }

    @Override
    @Transactional(readOnly = true)
    @Auditable(
            action = "TENANT_GET",
            targetType = "Tenant",
            targetIdExpression = "#tenantId",
            read = true)
    public Tenant get(String tenantId) {
        TenantId id = new TenantId(tenantId);
        return repository.findById(id).orElseThrow(() -> new TenantNotFoundException(tenantId));
    }

    @Override
    @Transactional(readOnly = true)
    @Auditable(action = "TENANT_LIST_ALL", targetType = "Tenant", read = true)
    public List<Tenant> listAll() {
        return repository.findAll();
    }
}
