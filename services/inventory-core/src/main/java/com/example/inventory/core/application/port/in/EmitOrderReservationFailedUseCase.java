package com.example.inventory.core.application.port.in;

/**
 * Order 引当失敗の補償イベントを発行するユースケース。
 *
 * <p>呼び出し元(Listener)から見ると、Reserve TX が rollback された後でも 補償イベントは確実に発行されるべきため、本ユースケース実装は REQUIRES_NEW
 * で別 TX として動かす(Reserve TX とは独立)。
 */
public interface EmitOrderReservationFailedUseCase {

    void emit(Command command);

    record Command(
            long orderAggregateId,
            String orderCode,
            String errorCode,
            String reason,
            String failedSkuCode,
            String failedLocationId) {

        public Command {
            if (orderCode == null || orderCode.isBlank())
                throw new IllegalArgumentException("orderCode は必須");
            if (errorCode == null || errorCode.isBlank())
                throw new IllegalArgumentException("errorCode は必須");
        }
    }
}
