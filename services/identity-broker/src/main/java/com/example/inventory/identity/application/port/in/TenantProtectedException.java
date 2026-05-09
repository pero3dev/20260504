package com.example.inventory.identity.application.port.in;

import org.springframework.http.HttpStatus;

import com.example.inventory.commons.error.BusinessException;

/**
 * 保護されたテナントに対する破壊的操作を拒否する。 現状唯一の保護対象は SUPER_ADMIN provisioning 用の {@code platform} テナント(deactivate
 * すると admin が完全にロックアウトされるため)。 409 Conflict。
 */
public class TenantProtectedException extends BusinessException {

    public static final String CODE = "ERR_TENANT_PROTECTED";

    public TenantProtectedException(String tenantId) {
        super("保護された tenant への破壊的操作は拒否: " + tenantId);
    }

    @Override
    public String errorCode() {
        return CODE;
    }

    @Override
    public HttpStatus statusCode() {
        return HttpStatus.CONFLICT;
    }
}
