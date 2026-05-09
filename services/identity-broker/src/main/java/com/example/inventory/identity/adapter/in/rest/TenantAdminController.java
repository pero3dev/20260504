package com.example.inventory.identity.adapter.in.rest;

import java.time.ZoneOffset;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.example.inventory.identity.adapter.in.rest.api.AdminTenantsApi;
import com.example.inventory.identity.adapter.in.rest.api.model.RegisterTenantRequest;
import com.example.inventory.identity.adapter.in.rest.api.model.TenantResource;
import com.example.inventory.identity.application.port.in.DeactivateTenantUseCase;
import com.example.inventory.identity.application.port.in.GetTenantUseCase;
import com.example.inventory.identity.application.port.in.RegisterTenantUseCase;
import com.example.inventory.identity.domain.model.Tenant;

/**
 * テナント lifecycle 管理 REST 入力アダプタ(A5、 ADR-0003 follow-up)。
 *
 * <p>OpenAPI 仕様から生成された {@link AdminTenantsApi} を実装。 not-found / already-exists は use case
 * 例外として上に抜け、 commons-error の {@code GlobalExceptionHandler} が RFC 7807 ProblemDetail に変換する。
 *
 * <p>{@code SecurityConfig.adminFilterChain} で {@code /v1/admin/**} は JWT 必須 + SUPER_ADMIN role
 * 必須に絞り込まれている。 SUPER_ADMIN role の provisioning(プラットフォーム管理用テナント membership)は別運用。
 */
@RestController
public class TenantAdminController implements AdminTenantsApi {

    private final RegisterTenantUseCase registerTenant;
    private final DeactivateTenantUseCase deactivateTenant;
    private final GetTenantUseCase getTenant;

    public TenantAdminController(
            RegisterTenantUseCase registerTenant,
            DeactivateTenantUseCase deactivateTenant,
            GetTenantUseCase getTenant) {
        this.registerTenant = registerTenant;
        this.deactivateTenant = deactivateTenant;
        this.getTenant = getTenant;
    }

    @Override
    public ResponseEntity<TenantResource> registerTenant(RegisterTenantRequest request) {
        Tenant tenant =
                registerTenant.register(
                        new RegisterTenantUseCase.Command(
                                request.getTenantId(), request.getDisplayName()));
        return ResponseEntity.status(HttpStatus.CREATED).body(toResource(tenant));
    }

    @Override
    public ResponseEntity<TenantResource> getTenant(String tenantId) {
        return ResponseEntity.ok(toResource(getTenant.get(tenantId)));
    }

    @Override
    public ResponseEntity<List<TenantResource>> listTenants() {
        return ResponseEntity.ok(getTenant.listAll().stream().map(this::toResource).toList());
    }

    @Override
    public ResponseEntity<TenantResource> deactivateTenant(String tenantId) {
        return ResponseEntity.ok(toResource(deactivateTenant.deactivate(tenantId)));
    }

    private TenantResource toResource(Tenant tenant) {
        TenantResource r = new TenantResource();
        r.setTenantId(tenant.tenantId().value());
        r.setDisplayName(tenant.displayName());
        r.setStatus(TenantResource.StatusEnum.fromValue(tenant.status().name()));
        r.setCreatedAt(tenant.createdAt().atOffset(ZoneOffset.UTC));
        if (tenant.deactivatedAt() != null) {
            r.setDeactivatedAt(tenant.deactivatedAt().atOffset(ZoneOffset.UTC));
        }
        return r;
    }
}
