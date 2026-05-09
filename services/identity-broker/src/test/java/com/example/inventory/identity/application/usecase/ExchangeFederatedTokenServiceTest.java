package com.example.inventory.identity.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import com.example.inventory.commons.persistence.SnowflakeIdGenerator;
import com.example.inventory.commons.tenant.TenantId;
import com.example.inventory.identity.application.port.in.AuthenticationFailedException;
import com.example.inventory.identity.application.port.in.ExchangeFederatedTokenUseCase;
import com.example.inventory.identity.application.port.out.IdpTokenVerifier;
import com.example.inventory.identity.application.port.out.IdpTokenVerifier.InvalidIdpTokenException;
import com.example.inventory.identity.application.port.out.TenantMembershipRepository;
import com.example.inventory.identity.application.port.out.TenantRepository;
import com.example.inventory.identity.application.port.out.TokenIssuer;
import com.example.inventory.identity.application.port.out.UserRepository;
import com.example.inventory.identity.config.FederationJitProperties;
import com.example.inventory.identity.domain.model.PasswordHash;
import com.example.inventory.identity.domain.model.RoleName;
import com.example.inventory.identity.domain.model.Tenant;
import com.example.inventory.identity.domain.model.TenantMembership;
import com.example.inventory.identity.domain.model.TenantStatus;
import com.example.inventory.identity.domain.model.User;
import com.example.inventory.identity.domain.model.UserEmail;
import com.example.inventory.identity.domain.model.UserId;

class ExchangeFederatedTokenServiceTest {

    private static final String COGNITO_ISSUER =
            "https://cognito-idp.ap-northeast-1.amazonaws.com/pool-1";

    private IdpTokenVerifier verifier;
    private UserRepository users;
    private TenantMembershipRepository memberships;
    private TenantRepository tenants;
    private TokenIssuer tokens;
    private SnowflakeIdGenerator idGenerator;

    @BeforeEach
    void setUp() {
        verifier = Mockito.mock(IdpTokenVerifier.class);
        users = Mockito.mock(UserRepository.class);
        memberships = Mockito.mock(TenantMembershipRepository.class);
        tenants = Mockito.mock(TenantRepository.class);
        tokens = Mockito.mock(TokenIssuer.class);
        idGenerator = Mockito.mock(SnowflakeIdGenerator.class);
    }

    private ExchangeFederatedTokenService service(FederationJitProperties jit) {
        return new ExchangeFederatedTokenService(
                verifier, users, memberships, tenants, tokens, idGenerator, jit);
    }

    private static FederationJitProperties jitDisabled() {
        return new FederationJitProperties(false, "", "VIEWER");
    }

    private static FederationJitProperties jitEnabled(String defaultTenantId) {
        return new FederationJitProperties(true, defaultTenantId, "VIEWER");
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
                service(jitDisabled())
                        .exchange(new ExchangeFederatedTokenUseCase.Command("provider-jwt"));

        assertThat(result.sessionToken()).isEqualTo("session.jwt.value");
        assertThat(result.expiresInSeconds()).isEqualTo(300L);
        assertThat(result.accessibleTenants()).containsExactly(m1);
        verify(users, never()).save(any());
        verify(memberships, never()).add(any());
    }

    @Test
    void 検証失敗は認証失敗にマップ_列挙攻撃対策() {
        when(verifier.verify("bad-jwt"))
                .thenThrow(new InvalidIdpTokenException("signature mismatch"));

        assertThatThrownBy(
                        () ->
                                service(jitDisabled())
                                        .exchange(
                                                new ExchangeFederatedTokenUseCase.Command(
                                                        "bad-jwt")))
                .isInstanceOf(AuthenticationFailedException.class);

        verify(users, never()).findByEmail(any());
        verify(tokens, never()).issueSessionToken(any(), any(), any());
    }

    @Test
    void JIT無効_検証成功するも内部User未存在は認証失敗_列挙攻撃対策() {
        when(verifier.verify("provider-jwt"))
                .thenReturn(new IdpTokenVerifier.Subject("ghost@example.com", COGNITO_ISSUER));
        when(users.findByEmail(any())).thenReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                service(jitDisabled())
                                        .exchange(
                                                new ExchangeFederatedTokenUseCase.Command(
                                                        "provider-jwt")))
                .isInstanceOf(AuthenticationFailedException.class);

        verify(users, never()).save(any());
        verify(memberships, never()).add(any());
        verify(tokens, never()).issueSessionToken(any(), any(), any());
    }

    @Test
    void subjectがemail形式でない場合も認証失敗() {
        when(verifier.verify("provider-jwt"))
                .thenReturn(new IdpTokenVerifier.Subject("not-an-email", COGNITO_ISSUER));

        assertThatThrownBy(
                        () ->
                                service(jitDisabled())
                                        .exchange(
                                                new ExchangeFederatedTokenUseCase.Command(
                                                        "provider-jwt")))
                .isInstanceOf(AuthenticationFailedException.class);

        verify(users, never()).findByEmail(any());
    }

    // ---------- JIT 経路 ----------

    @Test
    void JIT有効_unknown_user_は_default_tenant_で_provision_されセッション発行() {
        when(verifier.verify("provider-jwt"))
                .thenReturn(new IdpTokenVerifier.Subject("newuser@example.com", COGNITO_ISSUER));
        when(users.findByEmail(new UserEmail("newuser@example.com"))).thenReturn(Optional.empty());
        TenantId tenantId = new TenantId("acme");
        Tenant tenant =
                Tenant.restore(
                        tenantId,
                        "Acme Inc",
                        TenantStatus.ACTIVE,
                        Instant.parse("2026-01-01T00:00:00Z"),
                        null,
                        "ja");
        when(tenants.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(idGenerator.nextId()).thenReturn(424242L);
        // provision 後の findByUserId は新規 membership を返す。 add() は void で実 DB を持たないため
        // findByUserId 側で同一の row を返す stub で挙動を寄せる。
        TenantMembership newMembership =
                new TenantMembership(
                        new UserId(424242L),
                        tenantId,
                        "Acme Inc",
                        "ja",
                        List.of(new RoleName("VIEWER")),
                        List.of(),
                        List.of());
        when(memberships.findByUserId(new UserId(424242L))).thenReturn(List.of(newMembership));
        when(tokens.issueSessionToken(eq(new UserId(424242L)), eq(List.of(tenantId)), any()))
                .thenReturn("session.jit.jwt");

        ExchangeFederatedTokenUseCase.Result result =
                service(jitEnabled("acme"))
                        .exchange(new ExchangeFederatedTokenUseCase.Command("provider-jwt"));

        assertThat(result.sessionToken()).isEqualTo("session.jit.jwt");
        assertThat(result.accessibleTenants()).containsExactly(newMembership);

        // User INSERT 内容を確認
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(users).save(userCaptor.capture());
        User saved = userCaptor.getValue();
        assertThat(saved.id().value()).isEqualTo(424242L);
        assertThat(saved.email().value()).isEqualTo("newuser@example.com");
        assertThat(saved.displayName()).isEqualTo("newuser");
        assertThat(saved.passwordHash().value())
                .isEqualTo(ExchangeFederatedTokenService.FEDERATED_PASSWORD_HASH_SENTINEL);

        // Membership INSERT 内容を確認
        ArgumentCaptor<TenantMembership> mCaptor = ArgumentCaptor.forClass(TenantMembership.class);
        verify(memberships).add(mCaptor.capture());
        TenantMembership added = mCaptor.getValue();
        assertThat(added.userId().value()).isEqualTo(424242L);
        assertThat(added.tenantId()).isEqualTo(tenantId);
        assertThat(added.tenantDisplayName()).isEqualTo("Acme Inc");
        assertThat(added.tenantLocale()).isEqualTo("ja");
        assertThat(added.roleNames()).containsExactly("VIEWER");
    }

    @Test
    void JIT有効だがdefault_tenant_id_未設定は認証失敗() {
        when(verifier.verify("provider-jwt"))
                .thenReturn(new IdpTokenVerifier.Subject("newuser@example.com", COGNITO_ISSUER));
        when(users.findByEmail(any())).thenReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                service(jitEnabled(""))
                                        .exchange(
                                                new ExchangeFederatedTokenUseCase.Command(
                                                        "provider-jwt")))
                .isInstanceOf(AuthenticationFailedException.class);

        verify(users, never()).save(any());
        verify(memberships, never()).add(any());
    }

    @Test
    void JIT有効だがdefault_tenant_がDB不在は認証失敗() {
        when(verifier.verify("provider-jwt"))
                .thenReturn(new IdpTokenVerifier.Subject("newuser@example.com", COGNITO_ISSUER));
        when(users.findByEmail(any())).thenReturn(Optional.empty());
        when(tenants.findById(new TenantId("ghost"))).thenReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                service(jitEnabled("ghost"))
                                        .exchange(
                                                new ExchangeFederatedTokenUseCase.Command(
                                                        "provider-jwt")))
                .isInstanceOf(AuthenticationFailedException.class);

        verify(users, never()).save(any());
        verify(memberships, never()).add(any());
    }

    @Test
    void JIT有効だがdefault_tenant_がDEACTIVATEDは認証失敗() {
        when(verifier.verify("provider-jwt"))
                .thenReturn(new IdpTokenVerifier.Subject("newuser@example.com", COGNITO_ISSUER));
        when(users.findByEmail(any())).thenReturn(Optional.empty());
        TenantId tenantId = new TenantId("acme");
        Tenant deactivated =
                Tenant.restore(
                        tenantId,
                        "Acme",
                        TenantStatus.DEACTIVATED,
                        Instant.parse("2026-01-01T00:00:00Z"),
                        Instant.parse("2026-04-01T00:00:00Z"),
                        "ja");
        when(tenants.findById(tenantId)).thenReturn(Optional.of(deactivated));

        assertThatThrownBy(
                        () ->
                                service(jitEnabled("acme"))
                                        .exchange(
                                                new ExchangeFederatedTokenUseCase.Command(
                                                        "provider-jwt")))
                .isInstanceOf(AuthenticationFailedException.class);

        verify(users, never()).save(any());
        verify(memberships, never()).add(any());
    }

    @Test
    void 既存ユーザでもmembershipsが空なら認証失敗() {
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
        when(memberships.findByUserId(user.id())).thenReturn(List.of());

        assertThatThrownBy(
                        () ->
                                service(jitDisabled())
                                        .exchange(
                                                new ExchangeFederatedTokenUseCase.Command(
                                                        "provider-jwt")))
                .isInstanceOf(AuthenticationFailedException.class);

        verify(tokens, never()).issueSessionToken(any(), any(), any());
    }
}
