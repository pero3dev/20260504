package com.example.inventory.identity.domain.model;

/**
 * ユーザー集約。テナントを横断して存在する(Pool 方式、ADR-0003)。 テナントごとの所属・ロールは {@link TenantMembership} 集約が担う。
 *
 * <p>ID は Snowflake、email は世界一意。パスワードは BCrypt ハッシュとして保持する (生パスワードを保持しない設計)。
 */
public final class User {

    private final UserId id;
    private final UserEmail email;
    private final PasswordHash passwordHash;
    private final String displayName;
    private long version;

    public static User restore(
            UserId id,
            UserEmail email,
            PasswordHash passwordHash,
            String displayName,
            long version) {
        return new User(id, email, passwordHash, displayName, version);
    }

    public static User create(
            UserId id, UserEmail email, PasswordHash passwordHash, String displayName) {
        return new User(id, email, passwordHash, displayName, 0L);
    }

    private User(
            UserId id,
            UserEmail email,
            PasswordHash passwordHash,
            String displayName,
            long version) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.displayName = displayName == null ? "" : displayName;
        this.version = version;
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
}
