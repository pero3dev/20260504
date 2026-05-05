package com.example.inventory.core.application.port.in;

/**
 * 3PL 等の業態系から発信された {@code tpl.stock.movement.v1} を受けて、 Inventory Core 側の在庫を変動させるユースケース。
 *
 * <p>movementType ごとの挙動(MVP):
 *
 * <ul>
 *   <li>INBOUND: {@code inventory.receive(qty)}({@code available} 増加)
 *   <li>OUTBOUND: {@code reserve(qty)} → {@code ship(qty)} を即時実行({@code available} 減少)
 *   <li>ADJUSTMENT: MVP 範囲外。警告ログのみ
 * </ul>
 *
 * <p>失敗時の compensation 経路は本 MVP 未実装。3PL は物理的な入出庫の写像で、ビジネス例外は DLQ に 流して人手介入する運用が現実的。
 */
public interface ApplyStockMovementUseCase {

    void apply(Command command);

    record Command(
            long eventId,
            String movementCode,
            String skuCode,
            String locationId,
            String movementType,
            int quantity) {

        public Command {
            if (movementCode == null || movementCode.isBlank())
                throw new IllegalArgumentException("movementCode は必須");
            if (skuCode == null || skuCode.isBlank())
                throw new IllegalArgumentException("skuCode は必須");
            if (locationId == null || locationId.isBlank())
                throw new IllegalArgumentException("locationId は必須");
            if (movementType == null) throw new IllegalArgumentException("movementType は必須");
            if (quantity <= 0) throw new IllegalArgumentException("quantity は正の値");
        }
    }
}
