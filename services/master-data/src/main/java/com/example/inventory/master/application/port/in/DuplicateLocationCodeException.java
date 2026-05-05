package com.example.inventory.master.application.port.in;

import org.springframework.http.HttpStatus;

import com.example.inventory.commons.error.BusinessException;

/** 同テナント内に同じ Location コードが既に存在する。HTTP 409。 */
public class DuplicateLocationCodeException extends BusinessException {

    public static final String CODE = "ERR_LOCATION_CODE_DUPLICATE";

    public DuplicateLocationCodeException(String code) {
        super("Location コードが重複しています: " + code);
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
