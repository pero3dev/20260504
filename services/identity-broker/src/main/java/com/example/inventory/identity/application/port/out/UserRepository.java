package com.example.inventory.identity.application.port.out;

import java.util.Optional;

import com.example.inventory.identity.domain.model.User;
import com.example.inventory.identity.domain.model.UserEmail;
import com.example.inventory.identity.domain.model.UserId;

/** ユーザー検索 + 作成ポート。 SAML JIT provisioning 経路でのみ {@link #save(User)} を使う。 */
public interface UserRepository {

    Optional<User> findByEmail(UserEmail email);

    Optional<User> findById(UserId id);

    /**
     * 新規 User を INSERT する。 既存 User の更新は本 phase では未サポート(SAML JIT 経路のみ呼ぶ)。
     *
     * <p>caller が事前に Snowflake ID を採番した {@link User} を渡す。 email 一意制約衝突は同 IdP で
     * 並列 JIT が起きた時のみで、 SQL 例外として上げる(caller は AuthenticationFailedException に丸めて
     * 列挙攻撃対策と整合させる)。
     */
    void save(User user);
}
