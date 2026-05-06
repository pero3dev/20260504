package com.example.inventory.wholesale.application.port.in;

import org.springframework.http.HttpStatus;

import com.example.inventory.commons.error.BusinessException;

/**
 * 受注の状態遷移が現状態から不可能な場合に投げる。
 *
 * <p>HTTP 409 Conflict。例: CANCELLED の受注に対する ship、SHIPPED の受注に対する cancel など。
 */
public class OrderStateConflictException extends BusinessException {

    public static final String CODE = "ERR_ORDER_STATE_CONFLICT";

    public OrderStateConflictException(String message) {
        super(message);
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
