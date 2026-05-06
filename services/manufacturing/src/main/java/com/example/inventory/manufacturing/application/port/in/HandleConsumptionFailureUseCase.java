package com.example.inventory.manufacturing.application.port.in;

/**
 * Inventory Core から飛んできた {@code manufacturing.consumption.failed.v1} を受けて、 該当 WorkOrder を CANCELLED
 * に遷移させるユースケース(Saga 補償)。
 *
 * <p>{@code WorkOrder.cancel()} は PLANNED / RELEASED から CANCELLED への遷移を許す(冪等)。 既に COMPLETED
 * の指図に対する補償は通常発生しないが、at-least-once の遅延配信に備え サービス側でログ + スキップで吸収する想定。
 */
public interface HandleConsumptionFailureUseCase {

    void handle(Command command);

    record Command(long workOrderId, String workOrderCode, String errorCode, String reason) {

        public Command {
            if (workOrderId <= 0) throw new IllegalArgumentException("workOrderId は正の値");
            if (workOrderCode == null || workOrderCode.isBlank())
                throw new IllegalArgumentException("workOrderCode は必須");
        }
    }
}
