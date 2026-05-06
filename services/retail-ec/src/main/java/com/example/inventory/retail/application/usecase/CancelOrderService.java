package com.example.inventory.retail.application.usecase;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.inventory.commons.audit.Auditable;
import com.example.inventory.retail.application.port.in.CancelOrderUseCase;
import com.example.inventory.retail.application.port.in.OrderNotFoundException;
import com.example.inventory.retail.application.port.in.OrderStateConflictException;
import com.example.inventory.retail.application.port.out.OrderRepository;
import com.example.inventory.retail.domain.model.Order;
import com.example.inventory.retail.domain.model.OrderId;

/**
 * 注文キャンセル(業務側起因)サービス。
 *
 * <p>状態遷移 PLACED → CANCELLED と {@code retail.order.cancelled.v1} の発行を 1 トランザクション内で確定する。 Outbox
 * (ADR-0009)経由なので Kafka 直接発行せず、commit 後に publisher が拾う。
 *
 * <p>ドメイン純粋性のため domain 側は IllegalStateException を投げる。境界(application)で BusinessException に昇格させて HTTP
 * 409 へマップ(ADR-0006)。
 */
@Service
public class CancelOrderService implements CancelOrderUseCase {

    private final OrderRepository repository;

    public CancelOrderService(OrderRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    @Auditable(action = "ORDER_CANCEL", targetType = "Order", targetIdExpression = "#orderId")
    public Order cancel(long orderId) {
        Order order =
                repository
                        .findById(new OrderId(orderId))
                        .orElseThrow(() -> new OrderNotFoundException(orderId));
        try {
            order.cancel();
        } catch (IllegalStateException e) {
            throw new OrderStateConflictException(e.getMessage());
        }
        return repository.save(order);
    }
}
