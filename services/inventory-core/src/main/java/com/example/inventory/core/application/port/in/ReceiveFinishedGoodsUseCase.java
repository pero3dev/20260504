package com.example.inventory.core.application.port.in;

/**
 * Manufacturing の {@code manufacturing.work_order.completed.v1} を消費して、 完成品 SKU の {@code
 * Inventory.receive} を呼んで完成品在庫を増やすユースケース。
 *
 * <p>ADR-0017 に従い、Manufacturing は「release で部品消費 → complete で完成品入庫」の 2 段で フローを閉じる。本 UseCase は後者の起点。
 *
 * <p>失敗(Inventory レコード不在 = 完成品 SKU が master.product.v1 で投影されていない)は @Transactional ロールバック → DLQ
 * で観察(MVP 補償なし、ADR-0017 方針)。
 */
public interface ReceiveFinishedGoodsUseCase {

    void receive(Command command);

    record Command(
            long workOrderEventId,
            long workOrderAggregateId,
            String workOrderCode,
            String productSkuCode,
            String locationId,
            int quantity) {

        public Command {
            if (workOrderCode == null || workOrderCode.isBlank())
                throw new IllegalArgumentException("workOrderCode は必須");
            if (productSkuCode == null || productSkuCode.isBlank())
                throw new IllegalArgumentException("productSkuCode は必須");
            if (locationId == null || locationId.isBlank())
                throw new IllegalArgumentException("locationId は必須");
            if (quantity <= 0) throw new IllegalArgumentException("quantity は正の値");
        }
    }
}
