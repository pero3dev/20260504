package com.example.inventory.identity.application.port.in;

import org.springframework.http.HttpStatus;

import com.example.inventory.commons.error.BusinessException;

/** 同 (userId, tenantId) の membership が既存。 409 Conflict。 */
public class UserMembershipAlreadyExistsException extends BusinessException {

    public static final String CODE = "ERR_USER_MEMBERSHIP_ALREADY_EXISTS";

    public UserMembershipAlreadyExistsException(long userId, String tenantId) {
        super("user membership 既存: userId=" + userId + " tenantId=" + tenantId);
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
