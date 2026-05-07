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
import com.example.inventory.identity.application.port.in.AuthenticationFailedException;
import com.example.inventory.identity.application.port.in.ExchangeFederatedTokenUseCase;
import com.example.inventory.identity.application.port.out.IdpTokenVerifier;
import com.example.inventory.identity.application.port.out.IdpTokenVerifier.InvalidIdpTokenException;
import com.example.inventory.identity.application.port.out.TenantMembershipRepository;
import com.example.inventory.identity.application.port.out.TokenIssuer;
import com.example.inventory.identity.application.port.out.UserRepository;
import com.example.inventory.identity.domain.model.PasswordHash;
import com.example.inventory.identity.domain.model.RoleName;
import com.example.inventory.identity.domain.model.TenantMembership;
import com.example.inventory.identity.domain.model.User;
import com.example.inventory.identity.domain.model.UserEmail;
import com.example.inventory.identity.domain.model.UserId;

class ExchangeFederatedTokenServiceTest {

    private static final String COGNITO_ISSUER =
            "https://cognito-idp.ap-northeast-1.amazonaws.com/pool-1";

    private IdpTokenVerifier verifier;
    private UserRepository users;
    private TenantMembershipRepository memberships;
    private TokenIssuer tokens;
    private ExchangeFederatedTokenService service;

    @BeforeEach
    void setUp() {
        verifier = Mockito.mock(IdpTokenVerifier.class);
        users = Mockito.mock(UserRepository.class);
        memberships = Mockito.mock(TenantMembershipRepository.class);
        tokens = Mockito.mock(TokenIssuer.class);
        service = new ExchangeFederatedTokenService(verifier, users, memberships, tokens);
    }

    @Test
    void 検証成功と内部User有りでセッショントークンとテナント一覧を返す() {
        when(verifier.verify("provider-jwt"))
                .thenReturn(new IdpTokenVerifier.Subject("alice@example.com", COGNITO_ISSUER));
        User user =
                User.restore(
                        new UserId(100L),
                        new UserEmail("alice@example.com"),
                        new PasswordHash("$2a$10$..."),
                        "Alice",
                        1L);
        when(users.findByEmail(new UserEmail("alice@example.com"))).thenReturn(Optional.of(user));
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

        ExchangeFederatedTokenUseCase.Result result =
                service.exchange(new ExchangeFederatedTokenUseCase.Command("provider-jwt"));

        assertThat(result.sessionToken()).isEqualTo("session.jwt.value");
        assertThat(result.expiresInSeconds()).isEqualTo(300L);
        assertThat(result.accessibleTenants()).containsExactly(m1);
    }

    @Test
    void 検証失敗は認証失敗にマップ_列挙攻撃対策() {
        when(verifier.verify("bad-jwt"))
                .thenThrow(new InvalidIdpTokenException("signature mismatch"));

        assertThatThrownBy(
                        () ->
                                service.exchange(
                                        new ExchangeFederatedTokenUseCase.Command("bad-jwt")))
                .isInstanceOf(AuthenticationFailedException.class);

        verify(users, never()).findByEmail(any());
        verify(tokens, never()).issueSessionToken(any(), any(), any());
    }

    @Test
    void 検証成功するも内部User未存在は認証失敗_列挙攻撃対策() {
        when(verifier.verify("provider-jwt"))
                .thenReturn(new IdpTokenVerifier.Subject("ghost@example.com", COGNITO_ISSUER));
        when(users.findByEmail(any())).thenReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                service.exchange(
                                        new ExchangeFederatedTokenUseCase.Command("provider-jwt")))
                .isInstanceOf(AuthenticationFailedException.class);

        verify(tokens, never()).issueSessionToken(any(), any(), any());
    }

    @Test
    void subjectがemail形式でない場合も認証失敗() {
        when(verifier.verify("provider-jwt"))
                .thenReturn(new IdpTokenVerifier.Subject("not-an-email", COGNITO_ISSUER));

        assertThatThrownBy(
                        () ->
                                service.exchange(
                                        new ExchangeFederatedTokenUseCase.Command("provider-jwt")))
                .isInstanceOf(AuthenticationFailedException.class);

        verify(users, never()).findByEmail(any());
    }
}
