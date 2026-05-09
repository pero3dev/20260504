package com.example.inventory.commons.security;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * ADR-0023 即時 token revocation の Redis 実装を提供する auto-config。 spring-data-redis を classpath
 * に持つサービス(MVP では identity-broker のみ)で {@link RedisRevocationStore} を {@link RevocationStore} として登録。
 *
 * <p>class-level {@link ConditionalOnClass}({@link StringRedisTemplate} / {@link
 * RedisConnectionFactory})で Redis 不在 service では class-load 自体が起きないようにし、 method 引数の {@code
 * StringRedisTemplate} が {@link NoClassDefFoundError} を引き起こすのを防ぐ。 Spring Boot は
 * {@code @ConditionalOnClass} を ASM 経由で評価するため、 条件不成立なら本クラスを reflective に touch しない。
 *
 * <p>{@link NoOpRevocationStoreAutoConfiguration} が {@link AutoConfiguration#after} で本 config 後に
 * 順序付けされ、 Redis 変種が無い場合だけ NoOp に fallback する。
 */
@AutoConfiguration(
        afterName = "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration")
@ConditionalOnClass({StringRedisTemplate.class, RedisConnectionFactory.class})
public class RedisRevocationStoreAutoConfiguration {

    @Bean
    @ConditionalOnBean(RedisConnectionFactory.class)
    @ConditionalOnMissingBean(RevocationStore.class)
    public RevocationStore redisRevocationStore(StringRedisTemplate redis) {
        return new RedisRevocationStore(redis);
    }
}
