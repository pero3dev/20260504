package com.example.inventory.core.adapter.in.kafka;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * {@code manufacturing.work_order.released.v1} ペイロード。 Manufacturing 側 {@code
 * WorkOrderReleasedEvent} と項目構造を一致させる。
 *
 * <p>{@code plannedQuantity} と {@code plannedStartDate} は Inventory Core の消費処理では使用しないが、
 * 同一スキーマで読めるようフィールドを保持する({@code @JsonIgnoreProperties(ignoreUnknown = true)} で 将来の追加項目を許容)。
 *
 * <p>{@code components[].requiredQuantity} は Manufacturing 側で {@code quantityPerUnit *
 * plannedQuantity} を計算済みなので、Inventory Core はそのまま消費数量として使える。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WorkOrderReleasedMessage(
        long aggregateId,
        String code,
        String productSkuCode,
        String locationId,
        int plannedQuantity,
        List<Component> components,
        LocalDate plannedStartDate,
        Instant occurredAt) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Component(String componentSkuCode, int requiredQuantity) {}
}
