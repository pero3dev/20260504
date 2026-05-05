package com.example.inventory.core.application.port.in;

import org.springframework.http.HttpStatus;

import com.example.inventory.commons.error.BusinessException;

/**
 * 注文が指定する (skuCode, locationId) の在庫レコードが本テナントに存在しない。 業態系からのイベント駆動経路で、Master Data 投影や Inventory
 * レコード作成が未完了の状況で起きうる。
 *
 * <p>HTTP 422(リトライ可、投影遅延の可能性)。
 */
public class InventoryNotFoundForOrderException extends BusinessException {

    public static final String CODE = "ERR_INVENTORY_NOT_FOUND_FOR_ORDER";

    public InventoryNotFoundForOrderException(String skuCode, String locationId) {
        super("注文の在庫レコードが見つかりません: sku=" + skuCode + " location=" + locationId);
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
