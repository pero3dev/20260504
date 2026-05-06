package com.example.inventory.core.application.port.in;

import java.util.List;

/**
 * 業態系から飛んでくる「出荷確定」イベント(例: {@code wholesale.order.shipped.v1}) に対応した、明細毎の {@code Inventory.ship}
 * 呼出ユースケース。
 *
 * <p>前段の Reserve(D9 で実装済)で reserved 数量に乗っている前提なので、本ユースケースの ship は基本的に成功する。失敗(reserved
 * 不足)は本質的にはデータ整合性破綻で、 通常は MVP では補償経路は持たず DLQ で観察する(ADR-0017)。
 *
 * <p>1 メッセージ = 1 注文 = 1 トランザクション。明細毎に in-process で ship し、 1 件でも失敗すればロールバック。
 */
public interface ShipForOrderUseCase {

    void shipForOrder(Command command);

    record Command(long orderEventId, long orderAggregateId, String orderCode, List<Line> items) {

        public Command {
            if (orderCode == null || orderCode.isBlank())
                throw new IllegalArgumentException("orderCode は必須");
            if (items == null || items.isEmpty())
                throw new IllegalArgumentException("items は 1 行以上必要");
        }

        public record Line(int lineNo, String skuCode, String locationId, int quantity) {
            public Line {
                if (skuCode == null || skuCode.isBlank())
                    throw new IllegalArgumentException("skuCode は必須");
                if (locationId == null || locationId.isBlank())
                    throw new IllegalArgumentException("locationId は必須");
                if (quantity <= 0) throw new IllegalArgumentException("quantity は正の値");
            }
        }
    }
}
