package com.example.inventory.core.application.port.in;

import java.util.List;

/**
 * Retail/EC 等の業態系から受信した注文(retail.order.placed.v1)に対する 在庫引当ユースケース。
 *
 * <p>1 メッセージ = 1 注文 = 1 トランザクション。明細を一行ずつ in-process で reserve し、 途中で例外が発生したらロールバック(失敗時の
 * compensation は Phase 2 で別の補償イベントで担当)。
 *
 * <p>{@code orderEventId} は冪等性のための識別子(Outbox の event_id)。本 MVP では使わないが、 重複配信検出用のテーブル列に記録できるよう port
 * 上で保持しておく。
 */
public interface ReserveOrderUseCase {

    void reserveForOrder(Command command);

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
