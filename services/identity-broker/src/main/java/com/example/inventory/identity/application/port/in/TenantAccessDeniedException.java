package com.example.inventory.identity.application.port.in;

import org.springframework.http.HttpStatus;

import com.example.inventory.commons.error.BusinessException;

/** セッショントークンは有効だが、要求テナントに対するメンバーシップが無い場合。403。 */
public class TenantAccessDeniedException extends BusinessException {

    public static final String CODE = "ERR_TENANT_ACCESS_DENIED";

    public TenantAccessDeniedException(String tenantId) {
        super("テナントへのアクセス権がありません: " + tenantId);
    }

    @Override
    public String errorCode() {
        return CODE;
    }

    @Override
    public HttpStatus statusCode() {
        return HttpStatus.FORBIDDEN;
    }
}
