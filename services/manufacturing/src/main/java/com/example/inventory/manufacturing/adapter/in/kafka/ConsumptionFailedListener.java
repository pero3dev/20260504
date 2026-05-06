package com.example.inventory.manufacturing.adapter.in.kafka;

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
import com.example.inventory.manufacturing.application.port.in.HandleConsumptionFailureUseCase;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * {@code manufacturing.consumption.failed.v1} を購読する Kafka リスナ。
 *
 * <p>Inventory Core からの補償通知を受けて WorkOrder を CANCELLED に遷移させる。 業態ごとに補償トピックを分離しているため、Wholesale や
 * Retail/EC の補償(別 listener)とは独立。
 */
@Component
public class ConsumptionFailedListener {

    private static final Logger LOG = LoggerFactory.getLogger(ConsumptionFailedListener.class);
    private static final String HEADER_TENANT_ID = "tenant_id";

    private final HandleConsumptionFailureUseCase useCase;
    private final ObjectMapper objectMapper;

    public ConsumptionFailedListener(
            HandleConsumptionFailureUseCase useCase, ObjectMapper objectMapper) {
        this.useCase = useCase;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "manufacturing.consumption.failed.v1",
            groupId = "${spring.application.name}-compensation")
    public void onConsumptionFailed(ConsumerRecord<String, String> record, Acknowledgment ack)
            throws Exception {
        String tenantIdValue = headerValue(record, HEADER_TENANT_ID);
        if (tenantIdValue == null) {
            LOG.warn(
                    "tenant_id 無しの manufacturing.consumption.failed.v1 をスキップ partition={} offset={}",
                    record.partition(),
                    record.offset());
            ack.acknowledge();
            return;
        }
        try {
            TenantContext.set(new TenantId(tenantIdValue));
            ConsumptionFailedMessage msg =
                    objectMapper.readValue(record.value(), ConsumptionFailedMessage.class);
            useCase.handle(
                    new HandleConsumptionFailureUseCase.Command(
                            msg.aggregateId(), msg.workOrderCode(), msg.errorCode(), msg.reason()));
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
