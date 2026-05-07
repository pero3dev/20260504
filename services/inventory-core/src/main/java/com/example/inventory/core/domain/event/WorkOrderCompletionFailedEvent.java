package com.example.inventory.core.domain.event;

import java.time.Instant;

import com.example.inventory.commons.event.DomainEvent;

/**
 * Manufacturing WorkOrder 完了に伴う完成品 INBOUND が失敗したことを通知する補償イベント。 {@code
 * manufacturing.completion.failed.v1}。
 *
 * <p>{@link WorkOrderConsumptionFailedEvent}(release/部品消費失敗)と対称関係にある。 ただし WorkOrder
 * は既に物理現実で完成しているため、 補償の意味は **Manufacturing 集約の状態巻き戻しではなく観測 + 手動是正のトリガ**:
 *
 * <ul>
 *   <li>Manufacturing 側 {@code HandleCompletionFailureService} は WorkOrder の状態を触らない (COMPLETED
 *       は物理的に不可逆) — 監査ログ + 警告通知に留める
 *   <li>Notification は製造担当 / オペレータへ alert 送信
 *   <li>Audit は J-SOX 観点で行為記録
 *   <li>業務側で手動 INBOUND 是正(該当 SKU の `Inventory.receive` を運用 API 経由で再実行) or 完成品破棄登録
 * </ul>
 *
 * <p>主な発生原因:
 *
 * <ul>
 *   <li>{@code ERR_INVENTORY_NOT_FOUND_FOR_ORDER}(完成品 SKU の (sku, location) 在庫レコード未作成)
 *   <li>{@code ERR_UNKNOWN_SKU}(完成品 SKU 投影未到達 / 未登録)
 * </ul>
 *
 * <p>購読者: Manufacturing(audit + 通知トリガ)、Notification(製造担当 alert)、Audit(監査)。
 */
public record WorkOrderCompletionFailedEvent(
        long aggregateId,
        String workOrderCode,
        String errorCode,
        String reason,
        String productSkuCode,
        String locationId,
        int plannedQuantity,
        Instant occurredAt)
        implements DomainEvent {

    public static final String TOPIC = "manufacturing.completion.failed.v1";
    public static final String SCHEMA_VERSION = "1.0";

    @Override
    public String topic() {
        return TOPIC;
    }

    @Override
    public String schemaVersion() {
        return SCHEMA_VERSION;
    }
}
