package com.example.inventory.manufacturing.application.port.in;

import org.springframework.http.HttpStatus;

import com.example.inventory.commons.error.BusinessException;

/**
 * 製造指図計画時に productSkuCode で参照した BOM が見つからない。
 *
 * <p>HTTP 422(リトライ可、BOM マスタが後追いで登録される可能性)。
 */
public class BomNotFoundException extends BusinessException {

    public static final String CODE = "ERR_BOM_NOT_FOUND";

    public BomNotFoundException(String productSkuCode) {
        super("BOM が見つかりません: productSkuCode=" + productSkuCode);
    }

    @Override
    public String errorCode() {
        return CODE;
    }

    @Override
    public HttpStatus statusCode() {
        return HttpStatus.UNPROCESSABLE_ENTITY;
    }
}
