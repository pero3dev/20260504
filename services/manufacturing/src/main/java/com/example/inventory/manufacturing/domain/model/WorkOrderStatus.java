package com.example.inventory.manufacturing.domain.model;

/**
 * 製造指図のステータス。
 *
 * <p>遷移:
 *
 * <pre>
 *   PLANNED ──release()──▶ RELEASED ──complete()──▶ COMPLETED
 *      │                       │
 *      └───────cancel()────────┴─▶ CANCELLED
 * </pre>
 */
public enum WorkOrderStatus {
    PLANNED,
    RELEASED,
    COMPLETED,
    CANCELLED
}
