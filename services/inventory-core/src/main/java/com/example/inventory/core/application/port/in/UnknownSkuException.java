package com.example.inventory.core.application.port.in;

import org.springframework.http.HttpStatus;

import com.example.inventory.commons.error.BusinessException;
import com.example.inventory.core.domain.model.SkuId;

/**
 * 引当対象の在庫が参照する SKU が Master Data 投影に未登録だった。 Master Data 側に未作成、または投影が遅延しているケース。
 *
 * <p>HTTP 422 Unprocessable Entity にマッピング。リトライ可能(投影遅延の可能性があるため)。
 */
public class UnknownSkuException extends BusinessException {

    public static final String CODE = "ERR_UNKNOWN_SKU";

    public UnknownSkuException(SkuId code) {
        super("SKU が Master Data に未登録、または投影未到達: " + code.value());
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
