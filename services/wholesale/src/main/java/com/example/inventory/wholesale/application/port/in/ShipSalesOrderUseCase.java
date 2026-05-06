package com.example.inventory.wholesale.application.port.in;

import com.example.inventory.wholesale.domain.model.Order;

/**
 * 受注出荷確定ユースケース。
 *
 * <p>状態 PLANNED → SHIPPED に遷移し、{@code wholesale.order.shipped.v1} を Outbox 経由で発行する。 Inventory Core
 * はこれを listener で受けて {@code Inventory.ship} を呼び切る(ADR-0017)。
 *
 * <p>同一 PLACED 受注への重複 ship は冪等(状態のみ遷移)。SHIPPED で再呼出した場合は何も起きずに同オブジェクト返却。
 */
public interface ShipSalesOrderUseCase {

    Order ship(long orderId);
}
