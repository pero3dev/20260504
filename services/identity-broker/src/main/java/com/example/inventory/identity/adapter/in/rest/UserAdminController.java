package com.example.inventory.identity.adapter.in.rest;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.example.inventory.identity.adapter.in.rest.api.AdminUsersApi;
import com.example.inventory.identity.adapter.in.rest.api.model.UserResource;
import com.example.inventory.identity.application.port.in.GetUserUseCase;
import com.example.inventory.identity.domain.model.User;

/**
 * admin 向け user 参照 REST 入力アダプタ(A5 follow-up³)。
 *
 * <p>OpenAPI 仕様から生成された {@link AdminUsersApi} を実装。 not-found は {@link
 * com.example.inventory.identity.application.port.in.UserNotFoundException} として上に抜け、 commons-error
 * の GlobalExceptionHandler が RFC 7807 ProblemDetail に変換する。
 *
 * <p>REST 層では password hash を 一切 露出させない。 admin が user 詳細を見る時も hash は要らない (BCrypt なので leak しても緩衝
 * isあるが、設計上 admin に hash を見せる必要は無い)。
 *
 * <p>{@code SecurityConfig.adminFilterChain} で {@code /v1/admin/**} は JWT 必須 + SUPER_ADMIN role
 * 必須に絞り込まれている。
 */
@RestController
public class UserAdminController implements AdminUsersApi {

    private final GetUserUseCase getUser;

    public UserAdminController(GetUserUseCase getUser) {
        this.getUser = getUser;
    }

    @Override
    public ResponseEntity<UserResource> getUser(Long userId) {
        return ResponseEntity.ok(toResource(getUser.get(userId)));
    }

    @Override
    public ResponseEntity<List<UserResource>> listUsers() {
        return ResponseEntity.ok(getUser.listAll().stream().map(this::toResource).toList());
    }

    private UserResource toResource(User user) {
        UserResource r = new UserResource();
        r.setUserId(user.id().value());
        r.setEmail(user.email().value());
        r.setDisplayName(user.displayName());
        return r;
    }
}
