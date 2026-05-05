package com.example.inventory.readmodel.application.port.in;

import org.springframework.http.HttpStatus;

import com.example.inventory.commons.error.BusinessException;

/** Read Model に当該 inventory の投影が見つからない。HTTP 404。 */
public class InventoryNotFoundException extends BusinessException {

    public static final String CODE = "ERR_INVENTORY_NOT_FOUND";

    public InventoryNotFoundException(long inventoryId) {
        super("在庫が見つかりません: " + inventoryId);
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
