package com.example.inventory.retail.application.usecase;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.inventory.commons.audit.Auditable;
import com.example.inventory.retail.application.port.in.HandleReservationFailureUseCase;
import com.example.inventory.retail.application.port.out.OrderRepository;
import com.example.inventory.retail.domain.model.Order;
import com.example.inventory.retail.domain.model.OrderId;

/**
 * 在庫引当失敗の補償を受けて Order を CANCELLED に遷移させる。
 *
 * <p>未知の orderId(古い Order が無い等)は無視する(at-least-once の冗長配信を吸収)。 既に SHIPPED
 * の注文に補償が来たケース(時系列的に矛盾、再配信の遅延)も IllegalState を catch してスキップする — Saga
 * 上の整合性は別ジョブで監査する想定で、ここでは至誠処理を優先(L2 で出荷フロー導入後の防御策)。
 *
 * <p>@Auditable で {@code ORDER_CANCEL_BY_RESERVATION_FAILURE} を audit.log.v1 に記録。
 */
@Service
public class HandleReservationFailureService implements HandleReservationFailureUseCase {

    private static final Logger LOG =
            LoggerFactory.getLogger(HandleReservationFailureService.class);

    private final OrderRepository orderRepository;

    public HandleReservationFailureService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Override
    @Transactional
    @Auditable(
            action = "ORDER_CANCEL_BY_RESERVATION_FAILURE",
            targetType = "Order",
            targetIdExpression = "#command.orderCode")
    public void handle(Command command) {
        Order order = orderRepository.findById(new OrderId(command.orderId())).orElse(null);
        if (order == null) {
            LOG.warn(
                    "補償対象の注文が見つからずスキップ orderId={} orderCode={}",
                    command.orderId(),
                    command.orderCode());
            return;
        }
        try {
            order.cancel();
        } catch (IllegalStateException e) {
            // SHIPPED 状態への補償遅延などは整合性監査に任せ、ここではスキップ。
            LOG.warn("注文 {} は cancel 不可状態のため補償スキップ: {}", command.orderCode(), e.getMessage());
            return;
        }
        orderRepository.save(order);
        LOG.info(
                "在庫引当失敗により注文 {} を CANCELLED に遷移 errorCode={}",
                command.orderCode(),
                command.errorCode());
    }
}
