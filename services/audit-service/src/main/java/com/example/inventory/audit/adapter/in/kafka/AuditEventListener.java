package com.example.inventory.audit.adapter.in.kafka;

import java.nio.charset.StandardCharsets;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import com.example.inventory.audit.application.port.in.ProcessAuditEventUseCase;
import com.example.inventory.audit.domain.model.AuditOutcome;
import com.example.inventory.commons.tenant.TenantId;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * {@code audit.log.v1} を購読する Kafka リスナ。
 *
 * <p>処理:
 *
 * <ol>
 *   <li>ヘッダから {@code tenant_id} と {@code event_id} を取り出す
 *   <li>ペイロードを {@link AuditEventMessage} に decode
 *   <li>{@link ProcessAuditEventUseCase} に委譲(冪等性とハッシュチェーンはユースケース側)
 *   <li>成功 ack。失敗時は ack 無し → Spring Kafka の既定リトライ後 DLQ へ流す(application.yml で設定予定)
 * </ol>
 */
@Component
public class AuditEventListener {

    private static final Logger LOG = LoggerFactory.getLogger(AuditEventListener.class);
    private static final String HEADER_TENANT_ID = "tenant_id";
    private static final String HEADER_EVENT_ID = "event_id";

    private final ProcessAuditEventUseCase useCase;
    private final ObjectMapper objectMapper;

    public AuditEventListener(ProcessAuditEventUseCase useCase, ObjectMapper objectMapper) {
        this.useCase = useCase;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "audit.log.v1", groupId = "${spring.application.name}")
    public void onAuditEvent(ConsumerRecord<String, String> record, Acknowledgment ack)
            throws Exception {
        String tenantValue = headerValue(record, HEADER_TENANT_ID);
        String eventIdValue = headerValue(record, HEADER_EVENT_ID);
        if (tenantValue == null || eventIdValue == null) {
            LOG.warn(
                    "必須ヘッダ無しの監査イベントをスキップ tenant_id={} event_id={} offset={}",
                    tenantValue,
                    eventIdValue,
                    record.offset());
            ack.acknowledge();
            return;
        }

        AuditEventMessage msg = objectMapper.readValue(record.value(), AuditEventMessage.class);

        ProcessAuditEventUseCase.Command command =
                new ProcessAuditEventUseCase.Command(
                        new TenantId(tenantValue),
                        Long.parseLong(eventIdValue),
                        msg.action(),
                        msg.targetType(),
                        msg.targetId(),
                        msg.operatorUserId(),
                        msg.operatorTenantId(),
                        parseOutcome(msg.outcome()),
                        msg.errorCode(),
                        Boolean.TRUE.equals(msg.read()),
                        msg.inputJson(),
                        msg.occurredAt());

        ProcessAuditEventUseCase.Result result = useCase.process(command);
        ack.acknowledge();

        if (result == ProcessAuditEventUseCase.Result.APPENDED) {
            LOG.debug(
                    "audit append tenant={} event_id={} action={}",
                    tenantValue,
                    eventIdValue,
                    msg.action());
        }
    }

    private static String headerValue(ConsumerRecord<?, ?> record, String name) {
        Header h = record.headers().lastHeader(name);
        return h == null ? null : new String(h.value(), StandardCharsets.UTF_8);
    }

    private static AuditOutcome parseOutcome(String s) {
        if (s == null) return AuditOutcome.SUCCESS;
        try {
            return AuditOutcome.valueOf(s);
        } catch (IllegalArgumentException e) {
            return AuditOutcome.SUCCESS;
        }
    }
}
