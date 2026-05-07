package com.example.inventory.core.application.port.in;

/**
 * Manufacturing WorkOrder 完成品 INBOUND 失敗の補償イベントを発行するユースケース。
 *
 * <p>{@link EmitWorkOrderConsumptionFailedUseCase}(部品消費失敗)と対称関係。 完了側は WorkOrder
 * が物理的に不可逆な状態(COMPLETED)に入っているため、 補償は観測 + 手動是正のトリガで、 集約の状態巻き戻しは行わない。
 */
public interface EmitWorkOrderCompletionFailedUseCase {

    void emit(Command command);

    record Command(
            long workOrderAggregateId,
            String workOrderCode,
            String errorCode,
            String reason,
            String productSkuCode,
            String locationId,
            int plannedQuantity) {

        public Command {
            if (workOrderCode == null || workOrderCode.isBlank())
                throw new IllegalArgumentException("workOrderCode は必須");
            if (errorCode == null || errorCode.isBlank())
                throw new IllegalArgumentException("errorCode は必須");
            if (productSkuCode == null || productSkuCode.isBlank())
                throw new IllegalArgumentException("productSkuCode は必須");
        }
    }
}
