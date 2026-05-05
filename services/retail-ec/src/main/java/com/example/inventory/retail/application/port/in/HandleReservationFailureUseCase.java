package com.example.inventory.retail.application.port.in;

/**
 * Inventory Core から飛んできた {@code inventory.reservation.failed.v1} を受けて、 該当 Order を CANCELLED
 * に遷移させるユースケース(Saga 補償)。
 *
 * <p>{@code Order.cancel()} は冪等なので、同一 order に対する補償の二重配信は安全。
 */
public interface HandleReservationFailureUseCase {

    void handle(Command command);

    record Command(long orderId, String orderCode, String errorCode, String reason) {

        public Command {
            if (orderId <= 0) throw new IllegalArgumentException("orderId は正の値");
            if (orderCode == null || orderCode.isBlank())
                throw new IllegalArgumentException("orderCode は必須");
        }
    }
}
