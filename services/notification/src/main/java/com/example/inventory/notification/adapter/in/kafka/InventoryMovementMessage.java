package com.example.inventory.notification.adapter.in.kafka;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * inventory.movement.v1 ペイロードの注目フィールドのみ。 inventory-core の InventoryReservedEvent と一致する形(将来 Glue
 * Schema Registry で型契約)。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record InventoryMovementMessage(
        long aggregateId,
        String skuId,
        String locationId,
        long reservationId,
        int quantity,
        int availableAfter,
        int reservedAfter,
        long versionAfter) {}
