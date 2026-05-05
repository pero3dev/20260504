package com.example.inventory.tpl.domain.model;

/**
 * 入出庫種別。
 *
 * <ul>
 *   <li>INBOUND : 委託元から自社倉庫への入荷
 *   <li>OUTBOUND : 自社倉庫から出荷(出荷指示)
 *   <li>ADJUSTMENT: 棚卸調整(差異吸収)
 * </ul>
 */
public enum MovementType {
    INBOUND,
    OUTBOUND,
    ADJUSTMENT
}
