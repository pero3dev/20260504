package com.example.inventory.identity.application.port.in;

import org.springframework.http.HttpStatus;

import com.example.inventory.commons.error.BusinessException;

/** 該当 userId の user が存在しない。 404 Not Found。 */
public class UserNotFoundException extends BusinessException {

    public static final String CODE = "ERR_USER_NOT_FOUND";

    public UserNotFoundException(long userId) {
        super("user 不在: " + userId);
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
