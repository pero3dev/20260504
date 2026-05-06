package com.example.inventory.manufacturing.application.port.in;

import com.example.inventory.manufacturing.domain.model.WorkOrder;

/**
 * 製造指図完了ユースケース。
 *
 * <p>状態を RELEASED → COMPLETED に遷移し、{@code manufacturing.work_order.completed.v1} を Outbox
 * 経由で発行する。Inventory Core はこれを受けて完成品 SKU の {@code Inventory.receive} を呼び切る (ADR-0017 の完成品入庫の起点)。
 *
 * <p>同一 RELEASED 指図への重複 complete は冪等(状態のみ遷移)。COMPLETED で再呼出は no-op、 PLANNED/CANCELLED から complete は
 * WorkOrderStateConflictException(409)。
 */
public interface CompleteWorkOrderUseCase {

    WorkOrder complete(long workOrderId);
}
