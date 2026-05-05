package com.example.inventory.identity.application.port.out;

import java.util.Optional;

import com.example.inventory.identity.domain.model.User;
import com.example.inventory.identity.domain.model.UserEmail;
import com.example.inventory.identity.domain.model.UserId;

/** ユーザー検索ポート。MVP では参照のみ。 */
public interface UserRepository {

    Optional<User> findByEmail(UserEmail email);

    Optional<User> findById(UserId id);
}
