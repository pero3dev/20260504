package com.example.inventory.manufacturing.application.port.in;

import org.springframework.http.HttpStatus;

import com.example.inventory.commons.error.BusinessException;

public class DuplicateWorkOrderCodeException extends BusinessException {

    public static final String CODE = "ERR_WORK_ORDER_CODE_DUPLICATE";

    public DuplicateWorkOrderCodeException(String code) {
        super("製造指図コードが重複しています: " + code);
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
