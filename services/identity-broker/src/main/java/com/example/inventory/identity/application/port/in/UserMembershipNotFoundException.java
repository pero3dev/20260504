package com.example.inventory.identity.application.port.in;

import org.springframework.http.HttpStatus;

import com.example.inventory.commons.error.BusinessException;

/** 該当 (userId, tenantId) の membership が存在しない。 404 Not Found。 */
public class UserMembershipNotFoundException extends BusinessException {

    public static final String CODE = "ERR_USER_MEMBERSHIP_NOT_FOUND";

    public UserMembershipNotFoundException(long userId, String tenantId) {
        super("user membership 不在: userId=" + userId + " tenantId=" + tenantId);
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
