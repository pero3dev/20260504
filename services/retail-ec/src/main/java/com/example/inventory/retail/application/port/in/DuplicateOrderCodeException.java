package com.example.inventory.retail.application.port.in;

import org.springframework.http.HttpStatus;

import com.example.inventory.commons.error.BusinessException;

public class DuplicateOrderCodeException extends BusinessException {

    public static final String CODE = "ERR_ORDER_CODE_DUPLICATE";

    public DuplicateOrderCodeException(String code) {
        super("注文コードが重複しています: " + code);
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
