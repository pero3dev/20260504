package com.example.inventory.wholesale.application.usecase;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.inventory.commons.audit.Auditable;
import com.example.inventory.wholesale.application.port.in.HandleReservationFailureUseCase;
import com.example.inventory.wholesale.application.port.out.SalesOrderRepository;
import com.example.inventory.wholesale.domain.model.Order;
import com.example.inventory.wholesale.domain.model.OrderId;

/**
 * 在庫引当失敗の補償を受けて SalesOrder を CANCELLED に遷移させる。
 *
 * <p>未知の orderId(古い Order が無い等)は無視する(at-least-once の冗長配信を吸収)。 既に SHIPPED
 * の受注に補償が来たケース(時系列的に矛盾、再配信の遅延)も IllegalState を catch してスキップする — Saga
 * 上の整合性は別ジョブで監査する想定で、ここでは至誠処理を優先(L1 で出荷フロー導入後の防御策)。
 *
 * <p>@Auditable で {@code SALES_ORDER_CANCEL_BY_RESERVATION_FAILURE} を audit.log.v1 に記録。
 */
@Service
public class HandleReservationFailureService implements HandleReservationFailureUseCase {

    private static final Logger LOG =
            LoggerFactory.getLogger(HandleReservationFailureService.class);

    private final SalesOrderRepository orderRepository;

    public HandleReservationFailureService(SalesOrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Override
    @Transactional
    @Auditable(
            action = "SALES_ORDER_CANCEL_BY_RESERVATION_FAILURE",
            targetType = "SalesOrder",
            targetIdExpression = "#command.orderCode")
    public void handle(Command command) {
        Order order = orderRepository.findById(new OrderId(command.orderId())).orElse(null);
        if (order == null) {
            LOG.warn(
                    "補償対象の受注が見つからずスキップ orderId={} orderCode={}",
                    command.orderId(),
                    command.orderCode());
            return;
        }
        try {
            // 補償経由のキャンセルは release イベントを発行しない(Reserve 失敗時に reserved が
            // 乗っていないため、cancel() の release 経路を流すと InsufficientReserved になる)。
            order.cancelAfterReservationFailure();
        } catch (IllegalStateException e) {
            // SHIPPED 状態への補償遅延などは整合性監査に任せ、ここではスキップ。
            LOG.warn("受注 {} は cancel 不可状態のため補償スキップ: {}", command.orderCode(), e.getMessage());
            return;
        }
        orderRepository.save(order);
        LOG.info(
                "在庫引当失敗により受注 {} を CANCELLED に遷移 errorCode={}",
                command.orderCode(),
                command.errorCode());
    }
}
