package com.example.inventory.core.adapter.in.kafka;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * {@code tpl.stock.movement.v1} のペイロード。
 *
 * <p>3PL 側の {@code StockMovementPlannedEvent} を mirror した DTO。 Inventory Core は購読者として読むだけで、 produce
 * 側の record とは独立に保つ。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StockMovementMessage(
        long aggregateId,
        String code,
        String partnerCode,
        String skuCode,
        String locationId,
        String movementType,
        int quantity,
        String referenceCode,
        long versionAfter) {}
