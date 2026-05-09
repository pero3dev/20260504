package com.example.inventory.identity.domain.model;

import java.time.Instant;

/**
 * ユーザー集約。テナントを横断して存在する(Pool 方式、ADR-0003)。 テナントごとの所属・ロールは {@link TenantMembership} 集約が担う。
 *
 * <p>ID は Snowflake、email は世界一意。パスワードは BCrypt ハッシュとして保持する (生パスワードを保持しない設計)。
 *
 * <p>ライフサイクル: ACTIVE で作成、 admin が deactivate すると DEACTIVATED へ一方向遷移(A5 follow-up¹⁵)。 DEACTIVATED
 * user は AuthenticateService / ExchangeFederatedTokenService が認証失敗 (列挙対策のため通常の 401 と同じ
 * AuthenticationFailedException) で弾く。 既発行 access token は TTL 切れまで有効。
 */
public final class User {

    private final UserId id;
    private final UserEmail email;
    private final PasswordHash passwordHash;
    private final String displayName;
    private long version;
    private UserStatus status;
    private Instant deactivatedAt;

    public static User restore(
            UserId id,
            UserEmail email,
            PasswordHash passwordHash,
            String displayName,
            long version) {
        return restore(id, email, passwordHash, displayName, version, UserStatus.ACTIVE, null);
    }

    public static User restore(
            UserId id,
            UserEmail email,
            PasswordHash passwordHash,
            String displayName,
            long version,
            UserStatus status,
            Instant deactivatedAt) {
        return new User(id, email, passwordHash, displayName, version, status, deactivatedAt);
    }

    public static User create(
            UserId id, UserEmail email, PasswordHash passwordHash, String displayName) {
        return new User(id, email, passwordHash, displayName, 0L, UserStatus.ACTIVE, null);
    }

    private User(
            UserId id,
            UserEmail email,
            PasswordHash passwordHash,
            String displayName,
            long version,
            UserStatus status,
            Instant deactivatedAt) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.displayName = displayName == null ? "" : displayName;
        this.version = version;
        this.status = status == null ? UserStatus.ACTIVE : status;
        this.deactivatedAt = deactivatedAt;
    }

    /** ACTIVE → DEACTIVATED に遷移。 既に DEACTIVATED なら冪等(state 維持、 deactivatedAt は最初の値を保持)。 */
    public void deactivate(Instant now) {
        if (status == UserStatus.DEACTIVATED) return;
        this.status = UserStatus.DEACTIVATED;
        this.deactivatedAt = now;
    }

    public UserId id() {
        return id;
    }

    public UserEmail email() {
        return email;
    }

    public PasswordHash passwordHash() {
        return passwordHash;
    }

    public String displayName() {
        return displayName;
    }

    public long version() {
        return version;
    }

    public UserStatus status() {
        return status;
    }

    public Instant deactivatedAt() {
        return deactivatedAt;
    }
}
