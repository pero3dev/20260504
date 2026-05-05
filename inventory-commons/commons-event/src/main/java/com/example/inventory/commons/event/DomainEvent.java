package com.example.inventory.commons.event;

import java.time.Instant;

/**
 * 集約から発行されるドメインイベントのマーカーインタフェース。
 *
 * <p>イベントは集約のDB書込と同一トランザクションで {@code outbox} テーブルに永続化され、 その後 {@link OutboxPublisher} によって非同期で Kafka
 * へ発行される (Transactional Outbox、ADR-0009)。
 *
 * <p>{@code eventId} はインタフェースに含めない。{@link DomainEventPublisher} の実装が 一元採番し、{@link OutboxRecord} と
 * Kafka メッセージヘッダにのみ載せる。 これにより、集約は ID 採番に関心を持たずに済み、payload 内の重複も避けられる。
 *
 * <p>具象イベント型は {@link #topic()} / {@link #schemaVersion()} を宣言する。 これらは Glue Schema Registry
 * のスキーマ参照に使われる。
 */
public interface DomainEvent {

    /** このイベントを発行した集約のID。 */
    long aggregateId();

    /** Kafkaトピック名(例: {@code "inventory.movement.v1"})。 */
    String topic();

    /** スキーマバージョン(例: {@code "1.0"})。Glue Schema Registry で使用する。 */
    String schemaVersion();

    /** 業務事象の発生時刻(発行時刻ではない)。 */
    Instant occurredAt();
}
