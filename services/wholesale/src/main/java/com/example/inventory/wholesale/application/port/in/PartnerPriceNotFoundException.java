package com.example.inventory.wholesale.application.port.in;

import org.springframework.http.HttpStatus;

import com.example.inventory.commons.error.BusinessException;

/**
 * 受注確定時に (partnerCode, skuCode) で参照した PartnerPrice が見つからない。 取引先と SKU の組み合わせが未契約の場合に発生する。
 *
 * <p>HTTP 422(リトライ可、契約マスタ投影が後追いされる可能性)。
 */
public class PartnerPriceNotFoundException extends BusinessException {

    public static final String CODE = "ERR_PARTNER_PRICE_NOT_FOUND";

    public PartnerPriceNotFoundException(String partnerCode, String skuCode) {
        super("取引先別価格が見つかりません: partner=" + partnerCode + " sku=" + skuCode);
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
