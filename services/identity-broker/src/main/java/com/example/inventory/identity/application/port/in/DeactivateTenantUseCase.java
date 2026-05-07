package com.example.inventory.identity.application.port.in;

import com.example.inventory.identity.domain.model.Tenant;

public interface DeactivateTenantUseCase {

    /**
     * @throws TenantNotFoundException 該当無し
     */
    Tenant deactivate(String tenantId);
}
