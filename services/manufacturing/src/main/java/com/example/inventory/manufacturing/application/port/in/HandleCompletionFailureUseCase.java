package com.example.inventory.manufacturing.application.port.in;

/**
 * Inventory Core から飛んできた {@code manufacturing.completion.failed.v1} を受けて、 WorkOrder の完了 + INBOUND
 * サイクルが業務エラーで完結しなかったことを記録するユースケース(Saga 補償の観測層)。
 *
 * <p>{@link HandleConsumptionFailureUseCase}(部品消費失敗 → cancel)とは異なり、 完成品 INBOUND 失敗時には WorkOrder
 * の集約状態は **触らない**。 理由は WorkOrder.complete() が物理現実(完成品が出来た)に対する記録で、 不可逆だから — 在庫だけが追従していない状態を "巻き戻す"
 * のは現実と矛盾する。
 *
 * <p>本ユースケースの実体は監査ログ + 警告メッセージで、 業務側の手動是正(運用 API で {@code Inventory.receive} を再実行 or 完成品破棄登録)を促す。
 */
public interface HandleCompletionFailureUseCase {

    void handle(Command command);

    record Command(
            long workOrderId,
            String workOrderCode,
            String errorCode,
            String reason,
            String productSkuCode,
            String locationId,
            int plannedQuantity) {

        public Command {
            if (workOrderId <= 0) throw new IllegalArgumentException("workOrderId は正の値");
            if (workOrderCode == null || workOrderCode.isBlank())
                throw new IllegalArgumentException("workOrderCode は必須");
            if (errorCode == null || errorCode.isBlank())
                throw new IllegalArgumentException("errorCode は必須");
        }
    }
}
