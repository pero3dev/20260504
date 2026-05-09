package com.example.inventory.identity.application.usecase;

import java.time.Clock;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.inventory.commons.audit.Auditable;
import com.example.inventory.commons.tenant.TenantId;
import com.example.inventory.identity.application.port.in.DeactivateTenantUseCase;
import com.example.inventory.identity.application.port.in.GetTenantUseCase;
import com.example.inventory.identity.application.port.in.RegisterTenantUseCase;
import com.example.inventory.identity.application.port.in.TenantAlreadyExistsException;
import com.example.inventory.identity.application.port.in.TenantNotFoundException;
import com.example.inventory.identity.application.port.in.TenantProtectedException;
import com.example.inventory.identity.application.port.out.TenantRepository;
import com.example.inventory.identity.domain.model.Tenant;

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

    private final TenantRepository repository;
    private final Clock clock;

    public TenantManagementService(TenantRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
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
        LOG.info("tenant 非活性化 tenantId={} status={}", tenant.tenantId().value(), tenant.status());
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
