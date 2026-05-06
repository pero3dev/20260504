package com.example.inventory.wholesale.application.port.in;

import com.example.inventory.wholesale.domain.model.Order;

/**
 * 受注キャンセル(業務側起因)ユースケース。
 *
 * <p>状態 PLACED → CANCELLED に遷移し、{@code wholesale.order.cancelled.v1} を Outbox 経由で発行する。 Inventory
 * Core はこれを listener で受けて {@code Inventory.release} を呼び reserved を解放する(ADR-0018)。
 *
 * <p>同一 PLACED 受注への重複 cancel は冪等(状態のみ遷移、 イベント発行なし)。CANCELLED で再呼出は no-op。 SHIPPED からは {@code
 * OrderStateConflictException}(409)。
 *
 * <p>補償経由のキャンセル(Reserve 失敗)は本ユースケースを使わず、 {@code HandleReservationFailureService} が直接 {@code
 * Order.cancelAfterReservationFailure()} を呼ぶ(ADR-0018)。
 */
public interface CancelSalesOrderUseCase {

    Order cancel(long orderId);
}
