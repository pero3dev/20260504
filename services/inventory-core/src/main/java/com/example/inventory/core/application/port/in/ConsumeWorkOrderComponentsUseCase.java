package com.example.inventory.core.application.port.in;

import java.util.List;

/**
 * Manufacturing の {@code manufacturing.work_order.released.v1} を消費して、
 * 製造指図の構成要素分の在庫を消費(reserve+ship)する。
 *
 * <p>Wholesale の {@link ReserveOrderUseCase} との違い: 各構成要素は引当だけでなく即時に出庫(ship)する。
 * 着手指示は「実際の製造で部品が消費される」を意味するためで、引当だけでは完成品の生産が始まったときに 残在庫(reserved 在庫の残)が宙ぶらりんになる。Saga
 * 補償時は別経路で「部品入庫イベント」を 流して回復する想定(MVP では補償は WorkOrder.cancel のみ、消費済み部品は既に出庫済として扱う)。
 *
 * <p>1 メッセージ = 1 製造指図 = 1 トランザクション。途中で 1 つでも在庫不足/レコード不在なら、 全構成要素の消費がロールバックされる(all-or-nothing)。
 *
 * <p>{@code workOrderEventId} は冪等性のための識別子(Outbox の event_id)。
 */
public interface ConsumeWorkOrderComponentsUseCase {

    void consume(Command command);

    record Command(
            long workOrderEventId,
            long workOrderAggregateId,
            String workOrderCode,
            String locationId,
            List<Line> components) {

        public Command {
            if (workOrderCode == null || workOrderCode.isBlank())
                throw new IllegalArgumentException("workOrderCode は必須");
            if (locationId == null || locationId.isBlank())
                throw new IllegalArgumentException("locationId は必須");
            if (components == null || components.isEmpty())
                throw new IllegalArgumentException("components は 1 つ以上必要");
        }

        public record Line(String componentSkuCode, int requiredQuantity) {
            public Line {
                if (componentSkuCode == null || componentSkuCode.isBlank())
                    throw new IllegalArgumentException("componentSkuCode は必須");
                if (requiredQuantity <= 0)
                    throw new IllegalArgumentException("requiredQuantity は正の値");
            }
        }
    }
}
