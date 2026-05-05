package com.example.inventory.retail.application.port.in;

import org.springframework.http.HttpStatus;

import com.example.inventory.commons.error.BusinessException;

public class OrderNotFoundException extends BusinessException {

    public static final String CODE = "ERR_ORDER_NOT_FOUND";

    public OrderNotFoundException(long orderId) {
        super("注文が見つかりません: " + orderId);
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
