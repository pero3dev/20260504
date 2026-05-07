package com.example.inventory.identity.application.port.in;

import com.example.inventory.identity.domain.model.Tenant;

public interface RegisterTenantUseCase {

    record Command(String tenantId, String displayName) {}

    /**
     * 新規 tenant を登録する。
     *
     * @throws TenantAlreadyExistsException 同 tenantId が既存
     * @throws IllegalArgumentException パターン違反 / displayName 空
     */
    Tenant register(Command command);
}
