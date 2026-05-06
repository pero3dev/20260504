package com.example.inventory.manufacturing.application.port.in;

import org.springframework.http.HttpStatus;

import com.example.inventory.commons.error.BusinessException;

public class WorkOrderNotFoundException extends BusinessException {

    public static final String CODE = "ERR_WORK_ORDER_NOT_FOUND";

    public WorkOrderNotFoundException(long workOrderId) {
        super("製造指図が見つかりません: " + workOrderId);
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
