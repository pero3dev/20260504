package com.example.inventory.identity.application.port.in;

import java.util.List;

import com.example.inventory.identity.domain.model.Tenant;

public interface GetTenantUseCase {

    /**
     * @throws TenantNotFoundException 該当無し
     */
    Tenant get(String tenantId);

    /** 全 tenant を返す(ACTIVE / DEACTIVATED 両方含む)。 */
    List<Tenant> listAll();
}
