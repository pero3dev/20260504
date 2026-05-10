package com.example.inventory.commons.security;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis-backed 実装(ADR-0023)。
 *
 * <p>key scheme: {@code revocation:user:<userId>} → value は revoke 時刻(ops debug 用、 値自体は読み出さない)。 TTL
 * = access token TTL(15min)で self-expire するため GC ジョブ不要。
 *
 * <p>Redis 不達は fail-open(ADR-0023):
 *
 * <ul>
 *   <li>{@link #isUserRevoked} は例外を握り潰して {@code false} を返す → request は通る(15min TTL を最終 fallback
 *       とする)
 *   <li>{@link #revokeUser} も例外を握り潰して warn ログのみ → admin DB 操作は完了済、 ops が retry 可能
 * </ul>
 */
public class RedisRevocationStore implements RevocationStore {

    private static final Logger LOG = LoggerFactory.getLogger(RedisRevocationStore.class);
    private static final String KEY_PREFIX = "revocation:user:";

    private final StringRedisTemplate redis;

    public RedisRevocationStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public boolean isUserRevoked(long userId) {
        try {
            return Boolean.TRUE.equals(redis.hasKey(KEY_PREFIX + userId));
        } catch (DataAccessException e) {
            LOG.warn(
                    "RevocationStore Redis read 失敗 fail-open で通す userId={}: {}",
                    userId,
                    e.toString());
            return false;
        }
    }

    @Override
    public void revokeUser(long userId, Duration ttl) {
        try {
            redis.opsForValue().set(KEY_PREFIX + userId, Instant.now().toString(), ttl);
            LOG.info("user revoke 登録完了 userId={} ttl={}s", userId, ttl.toSeconds());
        } catch (DataAccessException e) {
            LOG.warn(
                    "RevocationStore Redis write 失敗 admin DB 操作は完了済 userId={}: {}",
                    userId,
                    e.toString());
        }
    }

    /**
     * Redis {@code TTL} コマンドの戻り値:
     *
     * <ul>
     *   <li>正の数 → 残 TTL 秒、 revoke 中
     *   <li>{@code -1} → key 有り / TTL 無し(本実装では発生しないが防御的に empty 扱い)
     *   <li>{@code -2} (Spring では null) → key 無し、 revoke 無し
     * </ul>
     *
     * Redis 不達は fail-open で empty を返す(status read を 200 で成功させる)。
     */
    @Override
    public Optional<Duration> getRevocationTtl(long userId) {
        try {
            Long ttlSeconds = redis.getExpire(KEY_PREFIX + userId, TimeUnit.SECONDS);
            if (ttlSeconds == null || ttlSeconds <= 0) {
                return Optional.empty();
            }
            return Optional.of(Duration.ofSeconds(ttlSeconds));
        } catch (DataAccessException e) {
            LOG.warn(
                    "RevocationStore Redis TTL 読出失敗 fail-open で empty 返却 userId={}: {}",
                    userId,
                    e.toString());
            return Optional.empty();
        }
    }
}
