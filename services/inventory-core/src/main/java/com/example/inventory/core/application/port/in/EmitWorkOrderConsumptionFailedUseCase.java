package com.example.inventory.core.application.port.in;

/**
 * Manufacturing WorkOrder 部品消費失敗の補償イベントを発行するユースケース。
 *
 * <p>Wholesale 用 {@link EmitWholesaleReservationFailedUseCase} と同じ構造。 業態ごとにトピックを分離するため別 UseCase + 別
 * Service にしてある。
 */
public interface EmitWorkOrderConsumptionFailedUseCase {

    void emit(Command command);

    record Command(
            long workOrderAggregateId,
            String workOrderCode,
            String errorCode,
            String reason,
            String failedComponentSkuCode,
            String failedLocationId) {

        public Command {
            if (workOrderCode == null || workOrderCode.isBlank())
                throw new IllegalArgumentException("workOrderCode は必須");
            if (errorCode == null || errorCode.isBlank())
                throw new IllegalArgumentException("errorCode は必須");
        }
    }
}
