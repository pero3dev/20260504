package com.example.inventory.core.adapter.in.kafka;

import java.nio.charset.StandardCharsets;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import com.example.inventory.commons.error.BusinessException;
import com.example.inventory.commons.tenant.TenantContext;
import com.example.inventory.commons.tenant.TenantId;
import com.example.inventory.core.application.port.in.ApplyStockMovementUseCase;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * {@code tpl.stock.movement.v1} を購読する Kafka リスナ。
 *
 * <p>3PL から飛んできた入出庫予定を {@link ApplyStockMovementUseCase} に渡して在庫に反映させる。
 *
 * <p>失敗時の挙動:
 *
 * <ul>
 *   <li>{@link BusinessException}(在庫不足、SKU 未登録、Inventory 未投影など): 警告ログ + ack。 補償経路は MVP 未実装。3PL
 *       の入出庫は物理動作の写像でビジネスエラーは 通常人手介入の対象になる
 *   <li>その他想定外: ack せず Spring Kafka 既定 retry → DLQ
 * </ul>
 */
@Component
public class StockMovementListener {

    private static final Logger LOG = LoggerFactory.getLogger(StockMovementListener.class);
    private static final String HEADER_TENANT_ID = "tenant_id";
    private static final String HEADER_EVENT_ID = "event_id";

    private final ApplyStockMovementUseCase useCase;
    private final ObjectMapper objectMapper;

    public StockMovementListener(ApplyStockMovementUseCase useCase, ObjectMapper objectMapper) {
        this.useCase = useCase;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "tpl.stock.movement.v1",
            groupId = "${spring.application.name}-stock-movement")
    public void onStockMovement(ConsumerRecord<String, String> record, Acknowledgment ack)
            throws Exception {
        String tenantIdValue = headerValue(record, HEADER_TENANT_ID);
        if (tenantIdValue == null) {
            LOG.warn(
                    "tenant_id 無しの tpl.stock.movement.v1 をスキップ partition={} offset={}",
                    record.partition(),
                    record.offset());
            ack.acknowledge();
            return;
        }
        long eventId = parseEventId(record);
        StockMovementMessage msg =
                objectMapper.readValue(record.value(), StockMovementMessage.class);

        try {
            TenantContext.set(new TenantId(tenantIdValue));
            useCase.apply(
                    new ApplyStockMovementUseCase.Command(
                            eventId,
                            msg.code(),
                            msg.skuCode(),
                            msg.locationId(),
                            msg.movementType(),
                            msg.quantity()));
            ack.acknowledge();
            LOG.debug(
                    "StockMovement {} を反映 type={} qty={} tenant={}",
                    msg.code(),
                    msg.movementType(),
                    msg.quantity(),
                    tenantIdValue);
        } catch (BusinessException e) {
            LOG.warn(
                    "StockMovement {} の反映が業務理由で失敗(MVP は ack のみ)errorCode={} reason={}",
                    msg.code(),
                    e.errorCode(),
                    e.getMessage());
            ack.acknowledge();
        } finally {
            TenantContext.clear();
        }
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
