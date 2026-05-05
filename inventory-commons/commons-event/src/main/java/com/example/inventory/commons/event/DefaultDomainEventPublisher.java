package com.example.inventory.commons.event;

import java.time.Instant;

import org.slf4j.MDC;

import com.example.inventory.commons.persistence.SnowflakeIdGenerator;
import com.example.inventory.commons.tenant.TenantContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * {@link DomainEventPublisher} の標準実装。
 *
 * <p>役割は3つだけ:
 *
 * <ol>
 *   <li>Snowflake で eventId を採番する。
 *   <li>イベントを JSON にシリアライズする。
 *   <li>tenant_id / trace_id を付加して {@link OutboxRepository#append} で outbox に書く。
 * </ol>
 *
 * <p>呼び出し元のトランザクション(通常はユースケースの {@code @Transactional})に参加する。 これにより、集約のDB書込と outbox への append
 * が原子的になり、 Transactional Outbox(ADR-0009)の整合性が保たれる。
 */
public class DefaultDomainEventPublisher implements DomainEventPublisher {

    private final OutboxRepository outboxRepository;
    private final SnowflakeIdGenerator idGenerator;
    private final ObjectMapper objectMapper;

    public DefaultDomainEventPublisher(
            OutboxRepository outboxRepository,
            SnowflakeIdGenerator idGenerator,
            ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.idGenerator = idGenerator;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publish(DomainEvent event) {
        long eventId = idGenerator.nextId();
        String tenantId = TenantContext.required().value();
        String traceId = MDC.get("traceId");
        OutboxRecord rec =
                new OutboxRecord(
                        eventId,
                        tenantId,
                        event.topic(),
                        event.schemaVersion(),
                        event.aggregateId(),
                        serialize(event),
                        traceId,
                        event.occurredAt(),
                        Instant.now(),
                        false);
        outboxRepository.append(rec);
    }

    private String serialize(DomainEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "ドメインイベントのシリアライズに失敗しました: " + event.getClass().getSimpleName(), e);
        }
    }
}
