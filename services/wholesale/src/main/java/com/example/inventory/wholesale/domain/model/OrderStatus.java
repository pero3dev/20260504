package com.example.inventory.wholesale.domain.model;

/**
 * 受注ステータス。
 *
 * <p>遷移:
 *
 * <pre>
 *   PLACED ──ship()────▶ SHIPPED   (終端、cancel 不可)
 *      │
 *      └─cancel()──▶ CANCELLED
 * </pre>
 *
 * <p>SHIPPED 状態の取消は「返品(別ドメイン)」になるので本ステータスでは扱わない。 後続で APPROVED / INVOICED 等の中間状態を追加する余地は残してある。
 */
public enum OrderStatus {
    PLACED,
    SHIPPED,
    CANCELLED
}
