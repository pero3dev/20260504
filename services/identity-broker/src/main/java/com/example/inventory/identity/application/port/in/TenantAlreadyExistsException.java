package com.example.inventory.identity.application.port.in;

import org.springframework.http.HttpStatus;

import com.example.inventory.commons.error.BusinessException;

/** 同 tenantId のテナントが既に存在する。 409 Conflict。 */
public class TenantAlreadyExistsException extends BusinessException {

    public static final String CODE = "ERR_TENANT_ALREADY_EXISTS";

    public TenantAlreadyExistsException(String tenantId) {
        super("tenant 既存: " + tenantId);
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
