package com.example.inventory.commons.event;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.example.inventory.commons.persistence.SnowflakeIdGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * commons-event のスプリングオートコンフィグ。
 *
 * <p>サービスは依存に commons-event を含めるだけで、Outbox 発行の一式が 自動起動する。{@code platform.outbox.enabled=false}
 * で無効化可能。
 *
 * <p>サービス側で {@link OutboxRepository} の Bean を提供する必要がある(MyBatis 実装)。
 */
@AutoConfiguration(
        after = com.example.inventory.commons.persistence.PersistenceAutoConfiguration.class)
@ConditionalOnProperty(
        prefix = "platform.outbox",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
@EnableConfigurationProperties(OutboxProperties.class)
@EnableScheduling
public class OutboxAutoConfiguration {

    /** 設定値ベースの TenantDirectory。本番では Bean を上書きして Identity 連携実装に差し替える。 */
    @Bean
    @ConditionalOnMissingBean
    public TenantDirectory tenantDirectory(OutboxProperties properties) {
        return new StaticTenantDirectory(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public OutboxKafkaSender outboxKafkaSender(KafkaTemplate<String, String> kafkaTemplate) {
        // Kafka プロデューサ側の冪等性は外部設定で有効化する想定:
        //   spring.kafka.producer.properties[enable.idempotence]=true
        //   spring.kafka.producer.acks=all
        kafkaTemplate
                .getProducerFactory()
                .getConfigurationProperties()
                .computeIfAbsent(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, k -> true);
        return new OutboxKafkaSender(kafkaTemplate);
    }

    /**
     * スケジュール起動の Kafka ドレイナ。 テストや一時停止時のため {@code platform.outbox.publisher-enabled=false}
     * で個別に無効化可能。{@link DomainEventPublisher}(outbox 書込)は常に動かしたまま、 Kafka 発行ループだけ止められる。
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
            prefix = "platform.outbox",
            name = "publisher-enabled",
            havingValue = "true",
            matchIfMissing = true)
    public OutboxPublisher outboxPublisher(
            TenantDirectory tenantDirectory,
            OutboxRepository outboxRepository,
            OutboxKafkaSender sender,
            OutboxProperties properties) {
        return new OutboxPublisher(tenantDirectory, outboxRepository, sender, properties);
    }

    /** 集約の保存と同一トランザクションでイベントを Outbox に追記する標準実装。 DBを持つサービス向け(OutboxRepository Bean が利用可能なとき)。 */
    @Bean
    @ConditionalOnMissingBean(DomainEventPublisher.class)
    @ConditionalOnBean({OutboxRepository.class, SnowflakeIdGenerator.class, ObjectMapper.class})
    public DomainEventPublisher domainEventPublisher(
            OutboxRepository outboxRepository,
            SnowflakeIdGenerator idGenerator,
            ObjectMapper objectMapper) {
        return new DefaultDomainEventPublisher(outboxRepository, idGenerator, objectMapper);
    }

    /**
     * DBを持たないサービス(Read Model、Notification 等)向けの直接 Kafka 発行版。
     *
     * <p>適用条件:
     *
     * <ul>
     *   <li>{@link OutboxRepository} Bean が存在しない({@code @ConditionalOnMissingBean} で除外)
     *   <li>{@link OutboxKafkaSender} と {@link SnowflakeIdGenerator} が利用可能
     * </ul>
     *
     * <p>Outbox 版({@link DefaultDomainEventPublisher})と相互排他になるよう {@code @ConditionalOnMissingBean}
     * で {@code OutboxRepository.class} も指定している。
     */
    @Bean
    @ConditionalOnMissingBean(value = {DomainEventPublisher.class, OutboxRepository.class})
    @ConditionalOnBean({OutboxKafkaSender.class, SnowflakeIdGenerator.class, ObjectMapper.class})
    public DomainEventPublisher directKafkaDomainEventPublisher(
            OutboxKafkaSender sender, SnowflakeIdGenerator idGenerator, ObjectMapper objectMapper) {
        return new DirectKafkaDomainEventPublisher(sender, idGenerator, objectMapper);
    }
}
