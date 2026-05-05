package com.example.inventory.readmodel.config;

/**
 * Kafka コンシューマ設定は {@code application.yml} の {@code spring.kafka.listener.ack-mode}
 * 等で完結させているため、本クラスはプレースホルダ。
 *
 * <p>必要が生じた場合(例: ErrorHandler のカスタマイズ、複数 ListenerContainerFactory の併存) はここに Bean を定義する。
 */
public final class KafkaConsumerConfig {

    private KafkaConsumerConfig() {}
}
