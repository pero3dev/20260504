package com.example.inventory.core.adapter.in.kafka;

import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

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
import com.example.inventory.core.application.port.in.ConsumeWorkOrderComponentsUseCase;
import com.example.inventory.core.application.port.in.EmitWorkOrderConsumptionFailedUseCase;
import com.example.inventory.core.application.port.in.InventoryNotFoundForOrderException;
import com.example.inventory.core.application.port.in.UnknownSkuException;
import com.example.inventory.core.domain.model.InsufficientStockException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * {@code manufacturing.work_order.released.v1} を購読する Kafka リスナ。
 *
 * <p>Manufacturing から飛んできた製造指図リリースを受けて、{@link ConsumeWorkOrderComponentsUseCase}
 * に渡し各構成要素の在庫を消費(reserve+ship)する。Wholesale の {@code WholesaleOrderPlacedListener} と並列だが、 補償発行先トピックが
 * {@code manufacturing.consumption.failed.v1} に分離。
 *
 * <p>失敗時の挙動(Saga Phase 2):
 *
 * <ul>
 *   <li>{@link InsufficientStockException}(構成要素の在庫不足): 補償イベントを別 TX で発行 → ack
 *   <li>{@link UnknownSkuException} / {@link InventoryNotFoundForOrderException} (投影遅延の可能性):
 *       同様に補償イベントを発行 → ack
 *   <li>その他想定外の例外: ack せず Spring Kafka 既定 retry → DLQ
 * </ul>
 */
@Component
public class WorkOrderReleasedListener {

    private static final Logger LOG = LoggerFactory.getLogger(WorkOrderReleasedListener.class);
    private static final String HEADER_TENANT_ID = "tenant_id";
    private static final String HEADER_EVENT_ID = "event_id";

    private final ConsumeWorkOrderComponentsUseCase consumeUseCase;
    private final EmitWorkOrderConsumptionFailedUseCase failureEmitter;
    private final ObjectMapper objectMapper;

    public WorkOrderReleasedListener(
            ConsumeWorkOrderComponentsUseCase consumeUseCase,
            EmitWorkOrderConsumptionFailedUseCase failureEmitter,
            ObjectMapper objectMapper) {
        this.consumeUseCase = consumeUseCase;
        this.failureEmitter = failureEmitter;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "manufacturing.work_order.released.v1",
            groupId = "${spring.application.name}-work-order-consume")
    public void onWorkOrderReleased(ConsumerRecord<String, String> record, Acknowledgment ack)
            throws Exception {
        String tenantIdValue = headerValue(record, HEADER_TENANT_ID);
        if (tenantIdValue == null) {
            LOG.warn(
                    "tenant_id 無しの manufacturing.work_order.released.v1 をスキップ partition={} offset={}",
                    record.partition(),
                    record.offset());
            ack.acknowledge();
            return;
        }
        long eventId = parseEventId(record);

        WorkOrderReleasedMessage msg =
                objectMapper.readValue(record.value(), WorkOrderReleasedMessage.class);

        try {
            TenantContext.set(new TenantId(tenantIdValue));
            consumeUseCase.consume(
                    new ConsumeWorkOrderComponentsUseCase.Command(
                            eventId,
                            msg.aggregateId(),
                            msg.code(),
                            msg.locationId(),
                            msg.components().stream()
                                    .map(
                                            c ->
                                                    new ConsumeWorkOrderComponentsUseCase.Command
                                                            .Line(
                                                            c.componentSkuCode(),
                                                            c.requiredQuantity()))
                                    .collect(Collectors.toList())));
            ack.acknowledge();
            LOG.debug(
                    "WorkOrder {} の部品消費を完了({}構成要素) tenant={}",
                    msg.code(),
                    msg.components().size(),
                    tenantIdValue);
        } catch (BusinessException e) {
            String failedSku = firstComponentSkuOrEmpty(msg);
            LOG.warn(
                    "WorkOrder {} の部品消費が業務理由で失敗、補償発行: errorCode={} reason={}",
                    msg.code(),
                    e.errorCode(),
                    e.getMessage());
            failureEmitter.emit(
                    new EmitWorkOrderConsumptionFailedUseCase.Command(
                            msg.aggregateId(),
                            msg.code(),
                            e.errorCode(),
                            e.getMessage(),
                            failedSku,
                            msg.locationId()));
            ack.acknowledge();
        } finally {
            TenantContext.clear();
        }
    }

    private static String firstComponentSkuOrEmpty(WorkOrderReleasedMessage msg) {
        return msg.components().isEmpty() ? "" : msg.components().get(0).componentSkuCode();
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
