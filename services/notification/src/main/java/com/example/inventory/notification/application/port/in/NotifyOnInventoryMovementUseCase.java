package com.example.inventory.notification.application.port.in;

/**
 * inventory.movement.v1 を受信して、閾値割れ等の条件にマッチしたら通知を送るユースケース。
 *
 * <p>MVP のロジック: {@code availableAfter <= threshold} のとき通知を送る。 受信者は仮で {@code
 * ops@example.com}(将来は購読設定 / プリファレンス管理を別 use case で扱う)。
 */
public interface NotifyOnInventoryMovementUseCase {

    void notifyIfNeeded(Command command);

    /** Kafka 受信メッセージのフラットな写像。adapter 層で boxed する。 */
    record Command(
            long eventId,
            String tenantId,
            long inventoryId,
            String skuId,
            String locationId,
            int availableAfter,
            int reservedAfter,
            long versionAfter) {
        public Command {
            if (tenantId == null || tenantId.isBlank())
                throw new IllegalArgumentException("tenantId 必須");
            if (skuId == null || skuId.isBlank()) throw new IllegalArgumentException("skuId 必須");
        }
    }
}
