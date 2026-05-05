package com.example.inventory.commons.event;

import java.util.concurrent.CompletableFuture;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

/**
 * Outbox レコードを Kafka に発行するサービス。
 *
 * <p>キーは {@code tenantId:aggregateId} 形式(例 {@code "acme:100000000123"})。
 * 同一集約のイベントが必ず同じパーティションに入るため、Kafka がパーティション内順序を 保証してくれる(Read Model 等の購読側が単純な逐次適用で正しい状態を得られる)。
 * eventId は Snowflake で一意なので、ヘッダで重複検出に使う。
 *
 * <p>※ 現時点ではペイロードはJSON文字列をそのまま送る。 Glue Schema Registry とのSerializer連携は後続イテレーションで差し替える(TODO)。
 */
public class OutboxKafkaSender {

    private static final String HEADER_TENANT_ID = "tenant_id";
    private static final String HEADER_TRACE_ID = "trace_id";
    private static final String HEADER_SCHEMA_VERSION = "schema_version";
    private static final String HEADER_EVENT_ID = "event_id";

    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxKafkaSender(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public CompletableFuture<SendResult<String, String>> send(OutboxRecord record) {
        ProducerRecord<String, String> message =
                new ProducerRecord<>(
                        record.topic(),
                        /* partition */ null,
                        /* key */ partitionKey(record),
                        record.payloadJson());

        message.headers().add(new RecordHeader(HEADER_EVENT_ID, longToBytes(record.eventId())));
        message.headers().add(new RecordHeader(HEADER_TENANT_ID, record.tenantId().getBytes()));
        message.headers()
                .add(new RecordHeader(HEADER_SCHEMA_VERSION, record.schemaVersion().getBytes()));
        if (record.traceId() != null) {
            message.headers().add(new RecordHeader(HEADER_TRACE_ID, record.traceId().getBytes()));
        }

        return kafkaTemplate.send(message);
    }

    private static byte[] longToBytes(long value) {
        return Long.toString(value).getBytes();
    }

    private static String partitionKey(OutboxRecord record) {
        return record.tenantId() + ":" + record.aggregateId();
    }
}
