package com.example.inventory.commons.event;

import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.kafka.support.serializer.DeserializationException;

/**
 * Kafka consumer 共通の DLQ ハンドラ オートコンフィグ。
 *
 * <p>消費失敗時の挙動:
 *
 * <ol>
 *   <li>{@link KafkaDlqProperties#maxRetries()} 回まで Exponential Backoff でリトライ
 *   <li>リトライ尽きると {@link DeadLetterPublishingRecoverer} が {@code
 *       &lt;original-topic&gt;&lt;suffix&gt;} へ failed record を発行
 *   <li>非リトライ対象例外(deserialize 失敗、引数不正等)は即時 DLQ
 * </ol>
 *
 * <p>Spring Kafka の {@link DefaultErrorHandler}({@code CommonErrorHandler})Bean があれば、 Spring Boot
 * 自動設定が default {@code ConcurrentKafkaListenerContainerFactory} に適用するため、 Kafka
 * 消費を行う全サービスに本設定が一律で効く。
 *
 * <p>無効化: {@code platform.kafka.dlq.enabled=false}。
 *
 * <p>本番運用注意:
 *
 * <ul>
 *   <li>DLQ トピックの自動作成に頼らない(Terraform で事前作成)
 *   <li>Datadog で {@code <topic>.dlq} の流入数アラート(0 でない時刻に通知)
 *   <li>DLQ から本トピックへの再投入は人的判断 + 専用ツール(自動再投入禁止)
 * </ul>
 */
@AutoConfiguration
@ConditionalOnClass(DefaultErrorHandler.class)
@ConditionalOnProperty(
        prefix = "platform.kafka.dlq",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
@EnableConfigurationProperties(KafkaDlqProperties.class)
public class KafkaErrorHandlerAutoConfiguration {

    private static final Logger LOG =
            LoggerFactory.getLogger(KafkaErrorHandlerAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(KafkaOperations.class)
    public DeadLetterPublishingRecoverer dlqRecoverer(
            KafkaOperations<?, ?> template, KafkaDlqProperties props) {
        return new DeadLetterPublishingRecoverer(
                template, (record, ex) -> resolveDlqDestination(record, props));
    }

    /**
     * DLQ の宛先解決ロジック。{@code DeadLetterPublishingRecoverer} の内部 BiFunction として
     * 注入する処理本体を、ユニットテスト可能なように分離している。
     *
     * <p>partition=-1 で Kafka 側のパーティショナに任せる(DLQ topic のパーティション数が 元 topic と異なっても安全)。
     */
    static TopicPartition resolveDlqDestination(
            org.apache.kafka.clients.consumer.ConsumerRecord<?, ?> record,
            KafkaDlqProperties props) {
        return new TopicPartition(record.topic() + props.suffix(), -1);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(DeadLetterPublishingRecoverer.class)
    public DefaultErrorHandler kafkaErrorHandler(
            DeadLetterPublishingRecoverer recoverer, KafkaDlqProperties props) {
        ExponentialBackOffWithMaxRetries backoff =
                new ExponentialBackOffWithMaxRetries(props.maxRetries());
        backoff.setInitialInterval(props.initialIntervalMs());
        backoff.setMultiplier(props.multiplier());
        backoff.setMaxInterval(props.maxIntervalMs());

        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backoff);
        // リトライ対象外(永続的失敗、即 DLQ 行き)
        handler.addNotRetryableExceptions(
                DeserializationException.class,
                IllegalArgumentException.class,
                org.springframework.messaging.converter.MessageConversionException.class);
        // 各リトライ前後にログ出力(運用観測用)
        handler.setRetryListeners(
                (record, ex, deliveryAttempt) ->
                        LOG.warn(
                                "Kafka 消費リトライ topic={} partition={} offset={} attempt={} cause={}",
                                record.topic(),
                                record.partition(),
                                record.offset(),
                                deliveryAttempt,
                                ex.toString()));
        return handler;
    }
}
