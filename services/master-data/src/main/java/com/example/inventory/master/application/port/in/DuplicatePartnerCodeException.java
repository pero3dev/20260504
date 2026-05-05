package com.example.inventory.master.application.port.in;

import org.springframework.http.HttpStatus;

import com.example.inventory.commons.error.BusinessException;

public class DuplicatePartnerCodeException extends BusinessException {

    public static final String CODE = "ERR_PARTNER_CODE_DUPLICATE";

    public DuplicatePartnerCodeException(String code) {
        super("Partner コードが重複しています: " + code);
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
