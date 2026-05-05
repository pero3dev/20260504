package com.example.inventory.identity.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.example.inventory.commons.tenant.TenantId;
import com.example.inventory.identity.application.port.in.AuthenticateUseCase;
import com.example.inventory.identity.application.port.in.AuthenticationFailedException;
import com.example.inventory.identity.application.port.out.PasswordVerifier;
import com.example.inventory.identity.application.port.out.TenantMembershipRepository;
import com.example.inventory.identity.application.port.out.TokenIssuer;
import com.example.inventory.identity.application.port.out.UserRepository;
import com.example.inventory.identity.domain.model.PasswordHash;
import com.example.inventory.identity.domain.model.RoleName;
import com.example.inventory.identity.domain.model.TenantMembership;
import com.example.inventory.identity.domain.model.User;
import com.example.inventory.identity.domain.model.UserEmail;
import com.example.inventory.identity.domain.model.UserId;

class AuthenticateServiceTest {

    private UserRepository users;
    private TenantMembershipRepository memberships;
    private PasswordVerifier passwords;
    private TokenIssuer tokens;
    private AuthenticateService service;

    @BeforeEach
    void setUp() {
        users = Mockito.mock(UserRepository.class);
        memberships = Mockito.mock(TenantMembershipRepository.class);
        passwords = Mockito.mock(PasswordVerifier.class);
        tokens = Mockito.mock(TokenIssuer.class);
        service = new AuthenticateService(users, memberships, passwords, tokens);
    }

    @Test
    void 正しいクレデンシャルでセッショントークンとテナント一覧が返る() {
        User user =
                User.restore(
                        new UserId(100L),
                        new UserEmail("alice@example.com"),
                        new PasswordHash("$2a$10$..."),
                        "Alice",
                        1L);
        when(users.findByEmail(new UserEmail("alice@example.com"))).thenReturn(Optional.of(user));
        when(passwords.matches(eq("correct-password"), any())).thenReturn(true);

        TenantMembership m1 =
                new TenantMembership(
                        user.id(),
                        new TenantId("acme"),
                        "Acme",
                        List.of(new RoleName("INVENTORY_MANAGER")),
                        List.of("LOC-1"),
                        List.of());
        when(memberships.findByUserId(user.id())).thenReturn(List.of(m1));
        when(tokens.issueSessionToken(
                        eq(user.id()), eq(List.of(new TenantId("acme"))), any(Duration.class)))
                .thenReturn("session.jwt.value");

        AuthenticateUseCase.Result result =
                service.authenticate(
                        new AuthenticateUseCase.Command("alice@example.com", "correct-password"));

        assertThat(result.sessionToken()).isEqualTo("session.jwt.value");
        assertThat(result.expiresInSeconds()).isEqualTo(300L); // 5分
        assertThat(result.accessibleTenants()).containsExactly(m1);
    }

    @Test
    void メール存在しない場合は認証失敗で統一する_列挙攻撃対策() {
        when(users.findByEmail(any())).thenReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                service.authenticate(
                                        new AuthenticateUseCase.Command("nobody@example.com", "x")))
                .isInstanceOf(AuthenticationFailedException.class);

        verify(passwords, never()).matches(any(), any());
        verify(tokens, never()).issueSessionToken(any(), any(), any());
    }

    @Test
    void パスワード不一致は認証失敗() {
        User user =
                User.restore(
                        new UserId(100L),
                        new UserEmail("alice@example.com"),
                        new PasswordHash("$2a$10$..."),
                        "Alice",
                        1L);
        when(users.findByEmail(any())).thenReturn(Optional.of(user));
        when(passwords.matches(eq("wrong"), any())).thenReturn(false);

        assertThatThrownBy(
                        () ->
                                service.authenticate(
                                        new AuthenticateUseCase.Command(
                                                "alice@example.com", "wrong")))
                .isInstanceOf(AuthenticationFailedException.class);

        verify(tokens, never()).issueSessionToken(any(), any(), any());
    }

    @Test
    void メール形式違反も認証失敗で統一_列挙攻撃対策() {
        assertThatThrownBy(
                        () ->
                                service.authenticate(
                                        new AuthenticateUseCase.Command("not-an-email", "x")))
                .isInstanceOf(AuthenticationFailedException.class);

        verify(users, never()).findByEmail(any());
    }
}
