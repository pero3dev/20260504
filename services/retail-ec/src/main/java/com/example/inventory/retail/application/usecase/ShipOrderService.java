package com.example.inventory.retail.application.usecase;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.inventory.commons.audit.Auditable;
import com.example.inventory.retail.application.port.in.OrderNotFoundException;
import com.example.inventory.retail.application.port.in.OrderStateConflictException;
import com.example.inventory.retail.application.port.in.ShipOrderUseCase;
import com.example.inventory.retail.application.port.out.OrderRepository;
import com.example.inventory.retail.domain.model.Order;
import com.example.inventory.retail.domain.model.OrderId;

/**
 * 注文出荷確定サービス。
 *
 * <p>状態遷移 PLACED → SHIPPED と {@code retail.order.shipped.v1} の発行を 1 トランザクション内で確定する。 Outbox
 * (ADR-0009)経由なので Kafka 直接発行せず、commit 後に publisher が拾う。
 *
 * <p>ドメイン純粋性のため domain 側は IllegalStateException を投げる。境界(application)で BusinessException に昇格させて HTTP
 * 409 へマップ(ADR-0006)。
 */
@Service
public class ShipOrderService implements ShipOrderUseCase {

    private final OrderRepository repository;

    public ShipOrderService(OrderRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    @Auditable(action = "ORDER_SHIP", targetType = "Order", targetIdExpression = "#orderId")
    public Order ship(long orderId) {
        Order order =
                repository
                        .findById(new OrderId(orderId))
                        .orElseThrow(() -> new OrderNotFoundException(orderId));
        try {
            order.ship();
        } catch (IllegalStateException e) {
            throw new OrderStateConflictException(e.getMessage());
        }
        return repository.save(order);
    }
}
