package com.example.inventory.commons.event;

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
        // Kafka プロデューサ側の冪等性は外部設定で有効化する想定(application.yml 側で必須):
        //   spring.kafka.producer.properties[enable.idempotence]=true
        //   spring.kafka.producer.acks=all
        // Spring Kafka 3.3+ の getConfigurationProperties() は unmodifiable map を返すため
        // ここで補完しようとすると UnsupportedOperationException になる。設定漏れは別途 ArchUnit/CI で検出。
        return new OutboxKafkaSender(kafkaTemplate);
    }

    /**
     * スケジュール起動の Kafka ドレイナ。 テストや一時停止時のため {@code platform.outbox.publisher-enabled=false}
     * で個別に無効化可能。{@link DomainEventPublisher}(outbox 書込)は常に動かしたまま、 Kafka 発行ループだけ止められる。
     *
     * <p>OutboxRepository が無い stateless サービスでは Bean 生成をスキップする。
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(OutboxRepository.class)
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

    /**
     * DomainEventPublisher を 1 本に統合し、OutboxRepository の有無を ObjectProvider で実行時判定する。
     *
     * <p>{@code @ConditionalOnBean(OutboxRepository.class)} は auto-config 評価時点で
     * ユーザーの @ComponentScan が完了している保証が無く、サービスが OutboxRepository を持っていても 「Bean
     * 不在」と判定されるケースが発生する。実行時注入であれば確実に解決される。
     *
     * <ul>
     *   <li>OutboxRepository Bean あり → {@link DefaultDomainEventPublisher}(集約保存と同一 TX で outbox 追記)
     *   <li>無し → {@link DirectKafkaDomainEventPublisher}(Kafka 直送、stateless サービス向け)
     * </ul>
     */
    @Bean
    @ConditionalOnMissingBean(DomainEventPublisher.class)
    public DomainEventPublisher domainEventPublisher(
            org.springframework.beans.factory.ObjectProvider<OutboxRepository>
                    outboxRepositoryProvider,
            SnowflakeIdGenerator idGenerator,
            ObjectMapper objectMapper,
            OutboxKafkaSender outboxKafkaSender) {
        OutboxRepository outboxRepository = outboxRepositoryProvider.getIfAvailable();
        if (outboxRepository != null) {
            return new DefaultDomainEventPublisher(outboxRepository, idGenerator, objectMapper);
        }
        return new DirectKafkaDomainEventPublisher(outboxKafkaSender, idGenerator, objectMapper);
    }
}
