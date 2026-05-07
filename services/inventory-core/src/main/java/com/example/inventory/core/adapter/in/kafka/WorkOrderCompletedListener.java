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
import com.example.inventory.core.application.port.in.EmitWorkOrderCompletionFailedUseCase;
import com.example.inventory.core.application.port.in.InventoryNotFoundForOrderException;
import com.example.inventory.core.application.port.in.ReceiveFinishedGoodsUseCase;
import com.example.inventory.core.application.port.in.UnknownSkuException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * {@code manufacturing.work_order.completed.v1} を購読する Kafka リスナ。
 *
 * <p>Manufacturing から飛んできた製造指図完了イベントを受けて、{@link ReceiveFinishedGoodsUseCase} に渡し完成品 SKU の
 * Inventory.receive を呼ぶ。{@code WorkOrderReleasedListener}(部品消費)と対称関係にある(release で部品が消え、 complete
 * で完成品が増える)。
 *
 * <p>失敗時の挙動(Saga Phase 2、 ADR-0017 / ADR-0019 補償経路):
 *
 * <ul>
 *   <li>{@link UnknownSkuException} / {@link InventoryNotFoundForOrderException}(投影遅延 / 未登録):
 *       補償イベント {@code manufacturing.completion.failed.v1} を別 TX で発行 → ack
 *   <li>その他想定外の例外: ack せず Spring Kafka 既定 retry → DLQ
 * </ul>
 *
 * <p>完成品 INBOUND は WorkOrder の COMPLETED 状態(物理現実で不可逆)に対する後続処理のため、 補償は集約の状態巻き戻しではなく **観測 +
 * 手動是正のトリガ**。 Manufacturing 側 listener は audit / 通知のみを行い、 WorkOrder 状態は触らない。
 */
@Component
public class WorkOrderCompletedListener {

    private static final Logger LOG = LoggerFactory.getLogger(WorkOrderCompletedListener.class);
    private static final String HEADER_TENANT_ID = "tenant_id";
    private static final String HEADER_EVENT_ID = "event_id";

    private final ReceiveFinishedGoodsUseCase receiveUseCase;
    private final EmitWorkOrderCompletionFailedUseCase failureEmitter;
    private final ObjectMapper objectMapper;

    public WorkOrderCompletedListener(
            ReceiveFinishedGoodsUseCase receiveUseCase,
            EmitWorkOrderCompletionFailedUseCase failureEmitter,
            ObjectMapper objectMapper) {
        this.receiveUseCase = receiveUseCase;
        this.failureEmitter = failureEmitter;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "manufacturing.work_order.completed.v1",
            groupId = "${spring.application.name}-work-order-finished-goods")
    public void onWorkOrderCompleted(ConsumerRecord<String, String> record, Acknowledgment ack)
            throws Exception {
        String tenantIdValue = headerValue(record, HEADER_TENANT_ID);
        if (tenantIdValue == null) {
            LOG.warn(
                    "tenant_id 無しの manufacturing.work_order.completed.v1 をスキップ partition={} offset={}",
                    record.partition(),
                    record.offset());
            ack.acknowledge();
            return;
        }
        long eventId = parseEventId(record);

        WorkOrderCompletedMessage msg =
                objectMapper.readValue(record.value(), WorkOrderCompletedMessage.class);

        try {
            TenantContext.set(new TenantId(tenantIdValue));
            receiveUseCase.receive(
                    new ReceiveFinishedGoodsUseCase.Command(
                            eventId,
                            msg.aggregateId(),
                            msg.code(),
                            msg.productSkuCode(),
                            msg.locationId(),
                            msg.completedQuantity()));
            ack.acknowledge();
            LOG.debug(
                    "WorkOrder {} の完成品入庫を反映 productSkuCode={} qty={} tenant={}",
                    msg.code(),
                    msg.productSkuCode(),
                    msg.completedQuantity(),
                    tenantIdValue);
        } catch (BusinessException e) {
            LOG.warn(
                    "WorkOrder {} の完成品 INBOUND が業務理由で失敗、補償発行: errorCode={} reason={}",
                    msg.code(),
                    e.errorCode(),
                    e.getMessage());
            failureEmitter.emit(
                    new EmitWorkOrderCompletionFailedUseCase.Command(
                            msg.aggregateId(),
                            msg.code(),
                            e.errorCode(),
                            e.getMessage(),
                            msg.productSkuCode(),
                            msg.locationId(),
                            msg.completedQuantity()));
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
