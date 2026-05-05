package com.example.inventory.tpl.application.port.in;

import org.springframework.http.HttpStatus;

import com.example.inventory.commons.error.BusinessException;

public class StockMovementNotFoundException extends BusinessException {

    public static final String CODE = "ERR_STOCK_MOVEMENT_NOT_FOUND";

    public StockMovementNotFoundException(long id) {
        super("StockMovement が見つかりません: " + id);
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
