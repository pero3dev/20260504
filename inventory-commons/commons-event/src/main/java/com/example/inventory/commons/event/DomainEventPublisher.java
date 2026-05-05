package com.example.inventory.commons.event;

/**
 * アプリケーション層からドメインイベントを発行するためのポート。
 *
 * <p>{@code commons-event} の実装はサービスごとの {@code outbox} テーブルへ 現在のDBトランザクション内で書き込む。別途スケジュールされた {@link
 * OutboxPublisher} がKafkaへ発行する。アプリケーション層は Kafkaに直接話しかけない。
 */
public interface DomainEventPublisher {

    void publish(DomainEvent event);
}
