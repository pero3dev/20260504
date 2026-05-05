package com.example.inventory.commons.event;

import java.time.Instant;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import com.example.inventory.commons.persistence.SnowflakeIdGenerator;
import com.example.inventory.commons.tenant.TenantContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * DBを持たないサービス(Read Model、Notification 等)向けの {@link DomainEventPublisher} 実装。Outbox を経由せず Kafka
 * に直接発行する。
 *
 * <p>{@link DefaultDomainEventPublisher} と {@link OutboxKafkaSender} を再利用して
 * 同じヘッダ・シリアライズ規約を保つ。違いは「DBへの書込ステップが無い」点のみ。
 *
 * <p><b>制約と用途:</b>
 *
 * <ul>
 *   <li>at-most-once: Kafka 障害中はイベントが失われる(Outbox による永続化が無いため)
 *   <li>業務トランザクションとの原子性は保証されない
 *   <li>用途: 監査(audit.log.v1)等の冪等な「事実の記録」、または DB を持たないサービス
 *   <li>厳密な発行保証が必要なドメインイベントは {@link DefaultDomainEventPublisher} (Outbox + DB トランザクション)を選ぶこと
 * </ul>
 */
public class DirectKafkaDomainEventPublisher implements DomainEventPublisher {

    private static final Logger LOG =
            LoggerFactory.getLogger(DirectKafkaDomainEventPublisher.class);
    private static final long SEND_TIMEOUT_MS = 3000;

    private final OutboxKafkaSender sender;
    private final SnowflakeIdGenerator idGenerator;
    private final ObjectMapper objectMapper;

    public DirectKafkaDomainEventPublisher(
            OutboxKafkaSender sender, SnowflakeIdGenerator idGenerator, ObjectMapper objectMapper) {
        this.sender = sender;
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
        try {
            sender.send(rec).get(SEND_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.warn("DomainEvent 発行が中断されました eventId={}", eventId);
        } catch (ExecutionException | TimeoutException e) {
            // fire-and-forget の代償として失敗を吸収。本番では Datadog アラートで検知する。
            LOG.warn(
                    "DomainEvent 発行に失敗 eventId={} topic={}: {}",
                    eventId,
                    event.topic(),
                    e.toString());
        }
    }

    private String serialize(DomainEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "DomainEvent のシリアライズに失敗: " + event.getClass().getSimpleName(), e);
        }
    }
}
