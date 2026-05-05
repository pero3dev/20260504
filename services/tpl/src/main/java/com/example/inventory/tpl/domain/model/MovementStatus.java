package com.example.inventory.tpl.domain.model;

/**
 * 入出庫ステータス。
 *
 * <ul>
 *   <li>PLANNED : 予定計上(まだ実物の動きは無い)
 *   <li>RECEIVED : 入荷確認済み(INBOUND の確定)
 *   <li>DISPATCHED : 出荷確認済み(OUTBOUND の確定)
 *   <li>CANCELLED : 取消
 * </ul>
 */
public enum MovementStatus {
    PLANNED,
    RECEIVED,
    DISPATCHED,
    CANCELLED
}
