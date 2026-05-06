package com.example.inventory.retail.adapter.in.kafka;

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
import com.example.inventory.retail.application.port.in.HandleReservationFailureUseCase;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * {@code retail.reservation.failed.v1} を購読する Kafka リスナ。
 *
 * <p>Inventory Core からの補償通知を受けて Order を CANCELLED に遷移させる。 業態ごとに補償トピックを分離する方針(ADR-0016)に従う。
 */
@Component
public class ReservationFailedListener {

    private static final Logger LOG = LoggerFactory.getLogger(ReservationFailedListener.class);
    private static final String HEADER_TENANT_ID = "tenant_id";

    private final HandleReservationFailureUseCase useCase;
    private final ObjectMapper objectMapper;

    public ReservationFailedListener(
            HandleReservationFailureUseCase useCase, ObjectMapper objectMapper) {
        this.useCase = useCase;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "retail.reservation.failed.v1",
            groupId = "${spring.application.name}-compensation")
    public void onReservationFailed(ConsumerRecord<String, String> record, Acknowledgment ack)
            throws Exception {
        String tenantIdValue = headerValue(record, HEADER_TENANT_ID);
        if (tenantIdValue == null) {
            LOG.warn(
                    "tenant_id 無しの retail.reservation.failed.v1 をスキップ partition={} offset={}",
                    record.partition(),
                    record.offset());
            ack.acknowledge();
            return;
        }
        try {
            TenantContext.set(new TenantId(tenantIdValue));
            ReservationFailedMessage msg =
                    objectMapper.readValue(record.value(), ReservationFailedMessage.class);
            useCase.handle(
                    new HandleReservationFailureUseCase.Command(
                            msg.aggregateId(), msg.orderCode(), msg.errorCode(), msg.reason()));
            ack.acknowledge();
        } finally {
            TenantContext.clear();
        }
    }

    private static String headerValue(ConsumerRecord<?, ?> record, String name) {
        Header h = record.headers().lastHeader(name);
        return h == null ? null : new String(h.value(), StandardCharsets.UTF_8);
    }
}
