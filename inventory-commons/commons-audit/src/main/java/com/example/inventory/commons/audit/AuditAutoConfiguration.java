package com.example.inventory.commons.audit;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

import com.example.inventory.commons.event.DomainEventPublisher;
import com.example.inventory.commons.event.OutboxAutoConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * commons-audit のオートコンフィグ。
 *
 * <p>サービスは依存に commons-audit を含めるだけで、{@link AuditableAspect} と {@link AuditEventEmitter} が自動配線される。
 * {@link DomainEventPublisher} の Bean(commons-event 由来)が必要。
 *
 * <p>{@code @AutoConfiguration(after = OutboxAutoConfiguration.class)} で commons-event の publisher
 * 決定後に評価されることを保証する。 publisher が無い場合(KafkaTemplate も OutboxRepository も無いミニマル構成)は クラス全体が
 * {@code @ConditionalOnBean(DomainEventPublisher.class)} で skip される。
 *
 * <p>{@code platform.audit.enabled=false} で全停止可能(局所開発時のみ想定)。
 */
@AutoConfiguration(after = OutboxAutoConfiguration.class)
@ConditionalOnProperty(
        prefix = "platform.audit",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
@ConditionalOnBean(DomainEventPublisher.class)
@EnableAspectJAutoProxy
public class AuditAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public AuditEventEmitter auditEventEmitter(DomainEventPublisher publisher) {
        return new AuditEventEmitter(publisher);
    }

    @Bean
    @ConditionalOnMissingBean
    public AuditableAspect auditableAspect(AuditEventEmitter emitter, ObjectMapper objectMapper) {
        return new AuditableAspect(emitter, objectMapper);
    }
}
