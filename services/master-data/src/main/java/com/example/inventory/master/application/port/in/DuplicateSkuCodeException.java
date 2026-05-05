package com.example.inventory.master.application.port.in;

import org.springframework.http.HttpStatus;

import com.example.inventory.commons.error.BusinessException;

/** 同テナント内に同じ SKU コードが既に存在する。HTTP 409。 */
public class DuplicateSkuCodeException extends BusinessException {

    public static final String CODE = "ERR_SKU_CODE_DUPLICATE";

    public DuplicateSkuCodeException(String code) {
        super("SKU コードが重複しています: " + code);
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
