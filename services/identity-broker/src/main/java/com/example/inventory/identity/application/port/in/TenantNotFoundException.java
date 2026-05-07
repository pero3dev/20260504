package com.example.inventory.identity.application.port.in;

import org.springframework.http.HttpStatus;

import com.example.inventory.commons.error.BusinessException;

/** 該当 tenantId のテナントが存在しない。 404 Not Found。 */
public class TenantNotFoundException extends BusinessException {

    public static final String CODE = "ERR_TENANT_NOT_FOUND";

    public TenantNotFoundException(String tenantId) {
        super("tenant 不在: " + tenantId);
    }

    @Override
    public String errorCode() {
        return CODE;
    }

    @Override
    public HttpStatus statusCode() {
        return HttpStatus.NOT_FOUND;
    }
}
