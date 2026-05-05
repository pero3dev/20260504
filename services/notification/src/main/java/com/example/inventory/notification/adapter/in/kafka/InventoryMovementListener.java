package com.example.inventory.notification.adapter.in.kafka;

import java.nio.charset.StandardCharsets;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import com.example.inventory.notification.application.port.in.NotifyOnInventoryMovementUseCase;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * {@code inventory.movement.v1} を購読する Kafka リスナ。
 *
 * <p>処理:
 *
 * <ol>
 *   <li>ヘッダから {@code tenant_id}・{@code event_id} を取得(冪等チェックキー)。
 *   <li>ペイロードを {@link InventoryMovementMessage} にデシリアライズ。
 *   <li>{@link NotifyOnInventoryMovementUseCase} に委譲(閾値判定 + 送信 + 記録)。
 *   <li>成功した場合のみ手動 ack。失敗時は Spring Kafka の既定リトライ + DLQ で扱う。
 * </ol>
 */
@Component
public class InventoryMovementListener {

    private static final Logger LOG = LoggerFactory.getLogger(InventoryMovementListener.class);
    private static final String HEADER_TENANT_ID = "tenant_id";
    private static final String HEADER_EVENT_ID = "event_id";

    private final NotifyOnInventoryMovementUseCase useCase;
    private final ObjectMapper objectMapper;

    public InventoryMovementListener(
            NotifyOnInventoryMovementUseCase useCase, ObjectMapper objectMapper) {
        this.useCase = useCase;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "inventory.movement.v1", groupId = "${spring.application.name}")
    public void onMovement(ConsumerRecord<String, String> record, Acknowledgment ack)
            throws Exception {
        String tenantId = headerValue(record, HEADER_TENANT_ID);
        if (tenantId == null) {
            LOG.warn(
                    "tenant_id ヘッダ無しのメッセージをスキップ topic={} partition={} offset={}",
                    record.topic(),
                    record.partition(),
                    record.offset());
            ack.acknowledge();
            return;
        }
        long eventId = parseEventId(record);

        InventoryMovementMessage msg =
                objectMapper.readValue(record.value(), InventoryMovementMessage.class);
        useCase.notifyIfNeeded(
                new NotifyOnInventoryMovementUseCase.Command(
                        eventId,
                        tenantId,
                        msg.aggregateId(),
                        msg.skuId(),
                        msg.locationId(),
                        msg.availableAfter(),
                        msg.reservedAfter(),
                        msg.versionAfter()));
        ack.acknowledge();
    }

    private static long parseEventId(ConsumerRecord<?, ?> record) {
        String raw = headerValue(record, HEADER_EVENT_ID);
        if (raw == null) return 0L;
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static String headerValue(ConsumerRecord<?, ?> record, String name) {
        Header h = record.headers().lastHeader(name);
        return h == null ? null : new String(h.value(), StandardCharsets.UTF_8);
    }
}
