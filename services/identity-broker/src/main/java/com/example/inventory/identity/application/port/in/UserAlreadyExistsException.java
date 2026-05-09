package com.example.inventory.identity.application.port.in;

import org.springframework.http.HttpStatus;

import com.example.inventory.commons.error.BusinessException;

/** 同 email の user が既に存在する。 409 Conflict。 */
public class UserAlreadyExistsException extends BusinessException {

    public static final String CODE = "ERR_USER_ALREADY_EXISTS";

    public UserAlreadyExistsException(String email) {
        super("user 既存: " + email);
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
