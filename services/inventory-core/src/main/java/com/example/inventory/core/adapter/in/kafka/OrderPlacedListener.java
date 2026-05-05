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
import com.example.inventory.core.application.port.in.ReserveOrderUseCase;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * {@code retail.order.placed.v1} を購読する Kafka リスナ。
 *
 * <p>Retail/EC 等の業態系から飛んできた注文確定イベントを受けて、 注文明細を {@link ReserveOrderUseCase} に渡し各 Inventory を引当てる。
 *
 * <p>at-least-once。冪等性は Inventory 集約の楽観ロック + 後段の compensation で担保する想定 (Phase 2)。
 *
 * <p>例外時は手動 ack せず、Spring Kafka 既定 retry → DLQ に流す。Phase 2 で reserved 失敗時に compensating event
 * を発行する経路を追加する。
 */
@Component
public class OrderPlacedListener {

    private static final Logger LOG = LoggerFactory.getLogger(OrderPlacedListener.class);
    private static final String HEADER_TENANT_ID = "tenant_id";
    private static final String HEADER_EVENT_ID = "event_id";

    private final ReserveOrderUseCase useCase;
    private final ObjectMapper objectMapper;

    public OrderPlacedListener(ReserveOrderUseCase useCase, ObjectMapper objectMapper) {
        this.useCase = useCase;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "retail.order.placed.v1",
            groupId = "${spring.application.name}-order-reserve")
    public void onOrderPlaced(ConsumerRecord<String, String> record, Acknowledgment ack)
            throws Exception {
        String tenantIdValue = headerValue(record, HEADER_TENANT_ID);
        if (tenantIdValue == null) {
            LOG.warn(
                    "tenant_id 無しの retail.order.placed.v1 をスキップ partition={} offset={}",
                    record.partition(),
                    record.offset());
            ack.acknowledge();
            return;
        }
        long eventId = parseEventId(record);

        try {
            TenantContext.set(new TenantId(tenantIdValue));
            OrderPlacedMessage msg =
                    objectMapper.readValue(record.value(), OrderPlacedMessage.class);

            useCase.reserveForOrder(
                    new ReserveOrderUseCase.Command(
                            eventId,
                            msg.aggregateId(),
                            msg.code(),
                            msg.items().stream()
                                    .map(
                                            l ->
                                                    new ReserveOrderUseCase.Command.Line(
                                                            l.lineNo(),
                                                            l.skuCode(),
                                                            l.locationId(),
                                                            l.quantity()))
                                    .collect(Collectors.toList())));
            ack.acknowledge();
            LOG.debug(
                    "Order {} の引当を完了({}行) tenant={}",
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
