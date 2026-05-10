package com.example.inventory.identity.application.port.in;

/**
 * admin が特定 user の即時 revocation 状態を確認する read-only use case(A5 follow-up²², ADR-0023)。
 *
 * <p>{@link RevokeUserUseCase}(¹⁹)で登録 / {@link DeactivateUserUseCase}(¹⁵)/ {@link
 * RemoveUserMembershipUseCase}(¹⁴)/ tenant deactivate fanout(¹⁸)で自動登録された Redis 上の revocation 状態を、
 * ops が web UI / runbook 経由で確認できるようにする。 これまでは {@code redis-cli GET} で代用していた。
 *
 * <p>本 use case は DB を read-only で touch するだけ(user 存在確認 = 404 ガード)、 副作用無し。 audit chain には {@code
 * read=true} で記録し、 admin の参照行為自体を J-SOX 統制下に置く。
 */
public interface GetUserRevocationStatusUseCase {

    /**
     * @param userId 対象 user
     * @return revocation 状態(revoke 中なら残 TTL 秒、 そうでなければ {@code revoked=false} / {@code
     *     ttlSeconds=null})
     * @throws UserNotFoundException 該当 userId の user が存在しない(404)
     */
    RevocationStatus getStatus(long userId);

    /**
     * @param revoked Redis に revocation key が存在し TTL > 0 なら true
     * @param ttlSeconds 残 TTL 秒。 {@code revoked=false} なら null
     */
    record RevocationStatus(boolean revoked, Long ttlSeconds) {}
}
