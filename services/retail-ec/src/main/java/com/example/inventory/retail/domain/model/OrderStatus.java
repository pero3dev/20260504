package com.example.inventory.retail.domain.model;

/** 注文ステータス。MVP は PLACED / CANCELLED の 2 値のみ。後続で SHIPPED / REFUNDED 等を追加。 */
public enum OrderStatus {
    PLACED,
    CANCELLED
}
