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
import com.example.inventory.manufacturing.application.port.in.HandleCompletionFailureUseCase;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * {@code manufacturing.completion.failed.v1} を購読する Kafka リスナ。
 *
 * <p>Inventory Core から完成品 INBOUND 失敗の補償通知を受けて、 監査ログ + 警告のみを行う(WorkOrder 状態は触らない、 詳細は {@code
 * HandleCompletionFailureService} の Javadoc 参照)。
 */
@Component
public class CompletionFailedListener {

    private static final Logger LOG = LoggerFactory.getLogger(CompletionFailedListener.class);
    private static final String HEADER_TENANT_ID = "tenant_id";

    private final HandleCompletionFailureUseCase useCase;
    private final ObjectMapper objectMapper;

    public CompletionFailedListener(
            HandleCompletionFailureUseCase useCase, ObjectMapper objectMapper) {
        this.useCase = useCase;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "manufacturing.completion.failed.v1",
            groupId = "${spring.application.name}-completion-compensation")
    public void onCompletionFailed(ConsumerRecord<String, String> record, Acknowledgment ack)
            throws Exception {
        String tenantIdValue = headerValue(record, HEADER_TENANT_ID);
        if (tenantIdValue == null) {
            LOG.warn(
                    "tenant_id 無しの manufacturing.completion.failed.v1 をスキップ partition={} offset={}",
                    record.partition(),
                    record.offset());
            ack.acknowledge();
            return;
        }
        try {
            TenantContext.set(new TenantId(tenantIdValue));
            CompletionFailedMessage msg =
                    objectMapper.readValue(record.value(), CompletionFailedMessage.class);
            useCase.handle(
                    new HandleCompletionFailureUseCase.Command(
                            msg.aggregateId(),
                            msg.workOrderCode(),
                            msg.errorCode(),
                            msg.reason(),
                            msg.productSkuCode(),
                            msg.locationId(),
                            msg.plannedQuantity()));
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
