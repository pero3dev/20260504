package com.example.inventory.identity.application.port.in;

import java.util.List;

import com.example.inventory.identity.domain.model.User;

/** 管理者向けの user 参照 use case(A5 follow-up³)。 admin の参照行為自体が J-SOX 統制点のため、 実装側で監査記録する。 */
public interface GetUserUseCase {

    /**
     * @throws UserNotFoundException 該当無し
     */
    User get(long userId);

    /** 全 user を返す。 password hash は domain 上は保持するが、 admin REST 層では 露出させない。 */
    List<User> listAll();
}
