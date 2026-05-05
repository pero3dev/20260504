package com.example.inventory.readmodel.adapter.in.kafka;

import java.nio.charset.StandardCharsets;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import com.example.inventory.commons.tenant.TenantContext;
import com.example.inventory.commons.tenant.TenantId;
import com.example.inventory.readmodel.application.usecase.ApplyInventoryMovementService;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * {@code inventory.movement.v1} を購読する Kafka リスナ。
 *
 * <p>処理:
 *
 * <ol>
 *   <li>ヘッダから {@code tenant_id} を取り {@link TenantContext} に設定する。 これにより下流の Redis キー名前空間が tenant
 *       ごとに分離される。
 *   <li>ペイロードを {@link InventoryMovementMessage} にデシリアライズ。
 *   <li>{@link ApplyInventoryMovementService#applyReserved} で投影を更新。
 *   <li>成功した場合のみ手動 ack(オフセット commit)。失敗時は Spring Kafka の 既定リトライ + DLQ へ流す(application.yml で構成)。
 * </ol>
 *
 * <p>Glue Schema Registry 統合後は ObjectMapper でなく Avro/JSON-Schema deserializer に置換する(TODO)。
 */
@Component
public class InventoryMovementListener {

    private static final Logger LOG = LoggerFactory.getLogger(InventoryMovementListener.class);
    private static final String HEADER_TENANT_ID = "tenant_id";

    private final ApplyInventoryMovementService service;
    private final ObjectMapper objectMapper;

    public InventoryMovementListener(
            ApplyInventoryMovementService service, ObjectMapper objectMapper) {
        this.service = service;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "inventory.movement.v1", groupId = "${spring.application.name}")
    public void onMovement(ConsumerRecord<String, String> record, Acknowledgment ack)
            throws Exception {
        String tenantIdValue = headerValue(record, HEADER_TENANT_ID);
        if (tenantIdValue == null) {
            LOG.warn(
                    "tenant_id ヘッダ無しのメッセージをスキップ topic={} partition={} offset={}",
                    record.topic(),
                    record.partition(),
                    record.offset());
            ack.acknowledge();
            return;
        }
        try {
            TenantContext.set(new TenantId(tenantIdValue));
            InventoryMovementMessage msg =
                    objectMapper.readValue(record.value(), InventoryMovementMessage.class);
            service.applyReserved(
                    msg.aggregateId(),
                    msg.skuId(),
                    msg.locationId(),
                    msg.availableAfter(),
                    msg.reservedAfter(),
                    msg.versionAfter(),
                    msg.occurredAt());
            ack.acknowledge();
            LOG.debug(
                    "投影適用 tenant={} inventoryId={} version={}",
                    tenantIdValue,
                    msg.aggregateId(),
                    msg.versionAfter());
        } finally {
            TenantContext.clear();
        }
    }

    private static String headerValue(ConsumerRecord<?, ?> record, String name) {
        Header h = record.headers().lastHeader(name);
        return h == null ? null : new String(h.value(), StandardCharsets.UTF_8);
    }
}
