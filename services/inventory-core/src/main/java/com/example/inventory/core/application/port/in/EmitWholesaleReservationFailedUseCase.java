package com.example.inventory.core.application.port.in;

/**
 * Wholesale SalesOrder 引当失敗の補償イベントを発行するユースケース。
 *
 * <p>Retail/EC 用 {@link EmitOrderReservationFailedUseCase} と同じ構造。 業態ごとにトピックを分離するため別 UseCase + 別
 * Service にしてある。
 */
public interface EmitWholesaleReservationFailedUseCase {

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
