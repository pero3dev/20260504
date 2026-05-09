package com.example.inventory.identity.adapter.in.rest;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.example.inventory.identity.adapter.in.rest.api.AdminUsersApi;
import com.example.inventory.identity.adapter.in.rest.api.model.AddUserMembershipRequest;
import com.example.inventory.identity.adapter.in.rest.api.model.MembershipResource;
import com.example.inventory.identity.adapter.in.rest.api.model.RegisterUserRequest;
import com.example.inventory.identity.adapter.in.rest.api.model.UserResource;
import com.example.inventory.identity.application.port.in.AddUserMembershipUseCase;
import com.example.inventory.identity.application.port.in.GetUserUseCase;
import com.example.inventory.identity.application.port.in.RegisterUserUseCase;
import com.example.inventory.identity.application.port.in.RemoveUserMembershipUseCase;
import com.example.inventory.identity.domain.model.TenantMembership;
import com.example.inventory.identity.domain.model.User;

/**
 * admin 向け user REST 入力アダプタ(A5 follow-up³ + ¹² + ¹³)。
 *
 * <p>OpenAPI 仕様から生成された {@link AdminUsersApi} を実装。 not-found / already-exists / DEACTIVATED tenant は
 * use case 例外として上に抜け、 commons-error の GlobalExceptionHandler が RFC 7807 ProblemDetail に変換する。
 *
 * <p>REST 層では password hash を 一切 露出させない。 admin が user 詳細を見る時も hash は要らない (BCrypt なので leak しても緩衝
 * があるが、設計上 admin に hash を見せる必要は無い)。
 *
 * <p>{@code SecurityConfig.adminFilterChain} で {@code /v1/admin/**} は JWT 必須 + SUPER_ADMIN role
 * 必須に絞り込まれている。
 */
@RestController
public class UserAdminController implements AdminUsersApi {

    private final GetUserUseCase getUser;
    private final RegisterUserUseCase registerUser;
    private final AddUserMembershipUseCase addUserMembership;
    private final RemoveUserMembershipUseCase removeUserMembership;

    public UserAdminController(
            GetUserUseCase getUser,
            RegisterUserUseCase registerUser,
            AddUserMembershipUseCase addUserMembership,
            RemoveUserMembershipUseCase removeUserMembership) {
        this.getUser = getUser;
        this.registerUser = registerUser;
        this.addUserMembership = addUserMembership;
        this.removeUserMembership = removeUserMembership;
    }

    @Override
    public ResponseEntity<UserResource> getUser(Long userId) {
        return ResponseEntity.ok(toResource(getUser.get(userId)));
    }

    @Override
    public ResponseEntity<List<UserResource>> listUsers() {
        return ResponseEntity.ok(getUser.listAll().stream().map(this::toResource).toList());
    }

    @Override
    public ResponseEntity<UserResource> registerUser(RegisterUserRequest request) {
        User user =
                registerUser.register(
                        new RegisterUserUseCase.Command(
                                request.getEmail(),
                                request.getDisplayName(),
                                request.getTenantId(),
                                request.getRoleName()));
        return ResponseEntity.status(HttpStatus.CREATED).body(toResource(user));
    }

    @Override
    public ResponseEntity<MembershipResource> addUserMembership(
            Long userId, AddUserMembershipRequest request) {
        TenantMembership membership =
                addUserMembership.addMembership(
                        new AddUserMembershipUseCase.Command(
                                userId, request.getTenantId(), request.getRoleName()));
        return ResponseEntity.status(HttpStatus.CREATED).body(toMembershipResource(membership));
    }

    @Override
    public ResponseEntity<Void> removeUserMembership(Long userId, String tenantId) {
        removeUserMembership.removeMembership(
                new RemoveUserMembershipUseCase.Command(userId, tenantId));
        return ResponseEntity.noContent().build();
    }

    private UserResource toResource(User user) {
        UserResource r = new UserResource();
        r.setUserId(user.id().value());
        r.setEmail(user.email().value());
        r.setDisplayName(user.displayName());
        return r;
    }

    private MembershipResource toMembershipResource(TenantMembership m) {
        MembershipResource r = new MembershipResource();
        r.setUserId(m.userId().value());
        r.setTenantId(m.tenantId().value());
        r.setTenantDisplayName(m.tenantDisplayName());
        r.setTenantLocale(m.tenantLocale());
        r.setRoles(m.roleNames());
        return r;
    }
}
