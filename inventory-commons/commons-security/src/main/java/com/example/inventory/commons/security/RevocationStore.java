package com.example.inventory.commons.security;

import java.time.Duration;
import java.util.Optional;

/**
 * 即時 token revocation の保存ポート(ADR-0023)。
 *
 * <p>per-user 粒度。 access token TTL に等しい TTL で entries は self-expire するため GC ジョブ不要。 全 12 resource
 * server は {@link RevocationCheckFilter} 経由で {@link #isUserRevoked} を毎リクエスト 1 回読む(Redis なら 0.5ms
 * 想定)。
 *
 * <p>Identity Broker は offboarding (user deactivate / membership remove / tenant deactivate fanout)
 * の各 use case 完了後に {@link #revokeUser} を呼ぶ。
 *
 * <p>spring-data-redis が classpath / Bean として有効化されていれば {@code RedisRevocationStore} が自動配線される。
 * そうでなければ {@code NoOpRevocationStore} に fallback(test 環境 / Redis 未配線サービスでも起動できる)。
 */
public interface RevocationStore {

    /**
     * @return 該当 userId が revoke 済なら true。 Redis 不達等で判定不能なら false (fail-open、 ADR-0023)。
     */
    boolean isUserRevoked(long userId);

    /**
     * 該当 user を {@code ttl} の間 revoke 状態にする。 Redis 不達は warn ログのみで例外を上げない (admin 操作自体は DB に反映済、
     * fail-open)。
     */
    void revokeUser(long userId, Duration ttl);

    /**
     * 該当 userId の revoke 残 TTL を返す。 admin の状態確認 API 用。 Redis 不達は fail-open で {@link
     * Optional#empty()}(= 「revoke 無し」 と同じ扱いで status read を成功させる)。
     *
     * @return revoke 中なら残 TTL、 revoke されていない / 判定不能なら empty
     */
    Optional<Duration> getRevocationTtl(long userId);
}
