package com.example.inventory.core.adapter.in.kafka;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * {@code master.product.v1} のペイロード。 Master Data の {@code SkuCreatedEvent} と項目構造を一致させる(契約は schema
 * レジストリで保証する想定)。
 *
 * <p>Glue Schema Registry 統合後は本クラスを Avro 生成型に置換する(TODO)。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SkuMasterMessage(
        long aggregateId,
        String code,
        String name,
        String description,
        String unitOfMeasure,
        long versionAfter,
        Instant occurredAt) {}
