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

import com.example.inventory.commons.tenant.TenantContext;
import com.example.inventory.commons.tenant.TenantId;
import com.example.inventory.core.application.port.in.ReleaseForOrderUseCase;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * {@code retail.order.cancelled.v1} を購読し、{@link ReleaseForOrderUseCase} で reserved を解放する。
 *
 * <p>Wholesale の {@link WholesaleOrderCancelledListener} と完全並列。groupId のみ業態別に分離(ADR-0016)。
 *
 * <p>業務エラーは @Transactional でロールバック → ack せず Spring Kafka 既定 retry → DLQ。 reserved
 * 不足は通常起きないので、起きたら運用上の整合性破綻として人が見る。
 */
@Component
public class RetailOrderCancelledListener {

    private static final Logger LOG = LoggerFactory.getLogger(RetailOrderCancelledListener.class);
    private static final String HEADER_TENANT_ID = "tenant_id";
    private static final String HEADER_EVENT_ID = "event_id";

    private final ReleaseForOrderUseCase releaseUseCase;
    private final ObjectMapper objectMapper;

    public RetailOrderCancelledListener(
            ReleaseForOrderUseCase releaseUseCase, ObjectMapper objectMapper) {
        this.releaseUseCase = releaseUseCase;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "retail.order.cancelled.v1",
            groupId = "${spring.application.name}-retail-order-cancel")
    public void onRetailOrderCancelled(ConsumerRecord<String, String> record, Acknowledgment ack)
            throws Exception {
        String tenantIdValue = headerValue(record, HEADER_TENANT_ID);
        if (tenantIdValue == null) {
            LOG.warn(
                    "tenant_id 無しの retail.order.cancelled.v1 をスキップ partition={} offset={}",
                    record.partition(),
                    record.offset());
            ack.acknowledge();
            return;
        }
        long eventId = parseEventId(record);
        OrderCancelledMessage msg =
                objectMapper.readValue(record.value(), OrderCancelledMessage.class);

        try {
            TenantContext.set(new TenantId(tenantIdValue));
            releaseUseCase.releaseForOrder(
                    new ReleaseForOrderUseCase.Command(
                            eventId,
                            msg.aggregateId(),
                            msg.code(),
                            msg.items().stream()
                                    .map(
                                            l ->
                                                    new ReleaseForOrderUseCase.Command.Line(
                                                            l.lineNo(),
                                                            l.skuCode(),
                                                            l.locationId(),
                                                            l.quantity()))
                                    .collect(Collectors.toList())));
            ack.acknowledge();
            LOG.debug(
                    "Retail Order {} の引当解放を反映({}行) tenant={}",
                    msg.code(),
                    msg.items().size(),
                    tenantIdValue);
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
