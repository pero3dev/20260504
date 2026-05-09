package com.example.inventory.commons.security;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Redis 未配線サービス / test 環境向けの no-op 実装(ADR-0023)。
 *
 * <p>{@link #isUserRevoked} は常に {@code false}(全許可)、 {@link #revokeUser} は warn ログのみで実体は無し。
 * 設定漏れに気付ける よう publisher 側は warn を出して可視化する。
 */
public class NoOpRevocationStore implements RevocationStore {

    private static final Logger LOG = LoggerFactory.getLogger(NoOpRevocationStore.class);

    @Override
    public boolean isUserRevoked(long userId) {
        return false;
    }

    @Override
    public void revokeUser(long userId, Duration ttl) {
        LOG.warn(
                "RevocationStore = NoOp (Redis 未配線)。 userId={} の revoke を実行できない。"
                        + " production では spring-boot-starter-data-redis を依存に加えること",
                userId);
    }
}
