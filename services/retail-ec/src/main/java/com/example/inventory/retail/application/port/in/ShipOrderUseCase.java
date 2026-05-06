package com.example.inventory.retail.application.port.in;

import com.example.inventory.retail.domain.model.Order;

/**
 * 注文出荷確定ユースケース。
 *
 * <p>状態 PLACED → SHIPPED に遷移し、{@code retail.order.shipped.v1} を Outbox 経由で発行する。 Inventory Core はこれを
 * listener で受けて {@code Inventory.ship} を呼び切る(ADR-0017)。
 *
 * <p>同一 PLACED 注文への重複 ship は冪等(状態のみ遷移)。SHIPPED で再呼出した場合は何も起きずに同オブジェクト返却。
 */
public interface ShipOrderUseCase {

    Order ship(long orderId);
}
