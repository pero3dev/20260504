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
import com.example.inventory.core.application.port.in.ShipForOrderUseCase;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * {@code wholesale.order.shipped.v1} を購読する Kafka リスナ。
 *
 * <p>Wholesale から飛んできた出荷確定イベントを受けて、{@link ShipForOrderUseCase} に渡し各明細を ship する。 ADR-0017 に従い MVP
 * では失敗時の補償は持たず、業務エラーは @Transactional ロールバック → ack されないため Spring Kafka 既定 retry → DLQ で観察する。
 *
 * <p>Reserve は D9 の {@code WholesaleOrderPlacedListener} で済んでいる前提なので、 reserved 不足になることは
 * 通常は無い(なれば運用上のデータ整合性破綻として人が見る)。
 */
@Component
public class SalesOrderShippedListener {

    private static final Logger LOG = LoggerFactory.getLogger(SalesOrderShippedListener.class);
    private static final String HEADER_TENANT_ID = "tenant_id";
    private static final String HEADER_EVENT_ID = "event_id";

    private final ShipForOrderUseCase shipUseCase;
    private final ObjectMapper objectMapper;

    public SalesOrderShippedListener(ShipForOrderUseCase shipUseCase, ObjectMapper objectMapper) {
        this.shipUseCase = shipUseCase;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "wholesale.order.shipped.v1",
            groupId = "${spring.application.name}-wholesale-order-ship")
    public void onSalesOrderShipped(ConsumerRecord<String, String> record, Acknowledgment ack)
            throws Exception {
        String tenantIdValue = headerValue(record, HEADER_TENANT_ID);
        if (tenantIdValue == null) {
            LOG.warn(
                    "tenant_id 無しの wholesale.order.shipped.v1 をスキップ partition={} offset={}",
                    record.partition(),
                    record.offset());
            ack.acknowledge();
            return;
        }
        long eventId = parseEventId(record);

        SalesOrderShippedMessage msg =
                objectMapper.readValue(record.value(), SalesOrderShippedMessage.class);

        try {
            TenantContext.set(new TenantId(tenantIdValue));
            shipUseCase.shipForOrder(
                    new ShipForOrderUseCase.Command(
                            eventId,
                            msg.aggregateId(),
                            msg.code(),
                            msg.items().stream()
                                    .map(
                                            l ->
                                                    new ShipForOrderUseCase.Command.Line(
                                                            l.lineNo(),
                                                            l.skuCode(),
                                                            l.locationId(),
                                                            l.quantity()))
                                    .collect(Collectors.toList())));
            ack.acknowledge();
            LOG.debug(
                    "Wholesale SalesOrder {} の出荷確定を反映({}行) tenant={}",
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
