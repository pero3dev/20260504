package com.example.inventory.manufacturing.application.port.in;

import org.springframework.http.HttpStatus;

import com.example.inventory.commons.error.BusinessException;

/**
 * 製造指図の状態遷移が現状態から不可能な場合に投げる。
 *
 * <p>HTTP 409 Conflict。例: 既に COMPLETED の指図に対する release / cancel など。
 */
public class WorkOrderStateConflictException extends BusinessException {

    public static final String CODE = "ERR_WORK_ORDER_STATE_CONFLICT";

    public WorkOrderStateConflictException(String message) {
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
