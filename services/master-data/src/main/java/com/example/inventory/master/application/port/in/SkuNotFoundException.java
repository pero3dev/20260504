package com.example.inventory.master.application.port.in;

import org.springframework.http.HttpStatus;

import com.example.inventory.commons.error.BusinessException;

public class SkuNotFoundException extends BusinessException {

    public static final String CODE = "ERR_SKU_NOT_FOUND";

    public SkuNotFoundException(long skuId) {
        super("SKU が見つかりません: " + skuId);
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
