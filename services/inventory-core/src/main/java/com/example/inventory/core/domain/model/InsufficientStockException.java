package com.example.inventory.core.domain.model;

import com.example.inventory.commons.error.BusinessException;

/** 引当要求量が利用可能在庫を超えた。HTTP 409 Conflict にマッピングされる。 */
public class InsufficientStockException extends BusinessException {

    public static final String CODE = "ERR_INVENTORY_INSUFFICIENT";

    public InsufficientStockException(InventoryId id, Quantity available, Quantity requested) {
        super(
                "在庫不足: inventory=%d 利用可能=%d 要求=%d"
                        .formatted(id.value(), available.value(), requested.value()));
    }

    @Override
    public String errorCode() {
        return CODE;
    }
}
