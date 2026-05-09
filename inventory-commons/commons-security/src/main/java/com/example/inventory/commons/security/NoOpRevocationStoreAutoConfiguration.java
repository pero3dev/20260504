package com.example.inventory.commons.security;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * ADR-0023 即時 token revocation の no-op fallback。 Redis を依存に持たない 12 services で {@link
 * NoOpRevocationStore} を登録し、 {@link RevocationCheckFilter} の DI を満たす。
 *
 * <p>{@link AutoConfiguration#after} で {@link RedisRevocationStoreAutoConfiguration} の後に評価され、 Redis
 * 変種が既に登録済みなら {@link ConditionalOnMissingBean} で skip される(= Redis 有り service では NoOp は wire されない)。
 */
@AutoConfiguration(after = RedisRevocationStoreAutoConfiguration.class)
public class NoOpRevocationStoreAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(RevocationStore.class)
    public RevocationStore noOpRevocationStore() {
        return new NoOpRevocationStore();
    }
}
