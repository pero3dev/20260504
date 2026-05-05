package com.example.inventory.core.domain.model;

import com.example.inventory.commons.error.BusinessException;

/** 出荷要求量が引当済み在庫(reserved)を超えた。HTTP 409 Conflict にマッピングされる。 */
public class InsufficientReservedException extends BusinessException {

    public static final String CODE = "ERR_INVENTORY_RESERVED_INSUFFICIENT";

    public InsufficientReservedException(InventoryId id, Quantity reserved, Quantity requested) {
        super(
                "引当済み不足: inventory=%d 引当済=%d 要求=%d"
                        .formatted(id.value(), reserved.value(), requested.value()));
    }

    @Override
    public String errorCode() {
        return CODE;
    }
}
