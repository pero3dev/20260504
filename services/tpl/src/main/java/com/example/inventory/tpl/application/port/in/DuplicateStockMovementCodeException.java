package com.example.inventory.tpl.application.port.in;

import org.springframework.http.HttpStatus;

import com.example.inventory.commons.error.BusinessException;

public class DuplicateStockMovementCodeException extends BusinessException {

    public static final String CODE = "ERR_STOCK_MOVEMENT_CODE_DUPLICATE";

    public DuplicateStockMovementCodeException(String code) {
        super("StockMovement コードが重複しています: " + code);
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
