package com.example.inventory.wholesale.domain.model;

/** 受注ステータス。MVP は PLACED / CANCELLED。後続で APPROVED / SHIPPED / INVOICED 等を追加。 */
public enum OrderStatus {
    PLACED,
    CANCELLED
}
