package com.example.inventory.analytics.adapter.in.kafka;

import java.nio.charset.StandardCharsets;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import com.example.inventory.analytics.application.port.in.IngestOrderPlacedUseCase;
import com.example.inventory.analytics.domain.model.BusinessContext;
import com.example.inventory.commons.tenant.TenantContext;
import com.example.inventory.commons.tenant.TenantId;
import com.fasterxml.jackson.databind.ObjectMapper;

/** {@code wholesale.order.placed.v1} を購読して analytics 集計に取り込む。 */
@Component
public class WholesaleOrderPlacedListener {

    private static final Logger LOG = LoggerFactory.getLogger(WholesaleOrderPlacedListener.class);
    private static final String HEADER_TENANT_ID = "tenant_id";
    private static final String HEADER_EVENT_ID = "event_id";

    private final IngestOrderPlacedUseCase useCase;
    private final ObjectMapper objectMapper;

    public WholesaleOrderPlacedListener(
            IngestOrderPlacedUseCase useCase, ObjectMapper objectMapper) {
        this.useCase = useCase;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "wholesale.order.placed.v1",
            groupId = "${spring.application.name}-wholesale-order")
    public void onWholesaleOrderPlaced(ConsumerRecord<String, String> record, Acknowledgment ack)
            throws Exception {
        String tenantIdValue = headerValue(record, HEADER_TENANT_ID);
        if (tenantIdValue == null) {
            LOG.warn(
                    "tenant_id 無しの wholesale.order.placed.v1 をスキップ partition={} offset={}",
                    record.partition(),
                    record.offset());
            ack.acknowledge();
            return;
        }
        long eventId = parseEventId(record);

        OrderPlacedMessage msg = objectMapper.readValue(record.value(), OrderPlacedMessage.class);

        try {
            TenantContext.set(new TenantId(tenantIdValue));
            useCase.ingest(
                    new IngestOrderPlacedUseCase.Command(
                            eventId,
                            new TenantId(tenantIdValue),
                            BusinessContext.WHOLESALE,
                            msg.currency(),
                            msg.totalAmount(),
                            msg.occurredAt()));
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
