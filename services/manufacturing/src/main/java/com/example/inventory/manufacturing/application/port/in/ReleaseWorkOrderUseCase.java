package com.example.inventory.manufacturing.application.port.in;

import com.example.inventory.manufacturing.domain.model.WorkOrder;

/**
 * 製造指図リリース(着手指示)ユースケース。
 *
 * <p>状態を PLANNED → RELEASED に遷移し、{@code manufacturing.work_order.released.v1} を Outbox 経由で発行する。
 * Inventory Core はこれを受けて部品引当を起動する(D10 で配線)。
 *
 * <p>同一 PLANNED 指図への重複 release は冪等(状態のみ遷移、Order と同等)。RELEASED で再呼出した場合は何も起きずに同オブジェクト返却。
 */
public interface ReleaseWorkOrderUseCase {

    WorkOrder release(long workOrderId);
}
