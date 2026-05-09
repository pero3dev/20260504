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
import org.mockito.Mockito;

import com.example.inventory.commons.tenant.TenantId;
import com.example.inventory.identity.application.port.in.AuthenticationFailedException;
import com.example.inventory.identity.application.port.in.SelectTenantUseCase;
import com.example.inventory.identity.application.port.in.TenantAccessDeniedException;
import com.example.inventory.identity.application.port.out.TenantMembershipRepository;
import com.example.inventory.identity.application.port.out.TenantRepository;
import com.example.inventory.identity.application.port.out.TokenIssuer;
import com.example.inventory.identity.domain.model.RoleName;
import com.example.inventory.identity.domain.model.Tenant;
import com.example.inventory.identity.domain.model.TenantMembership;
import com.example.inventory.identity.domain.model.TenantStatus;
import com.example.inventory.identity.domain.model.UserId;

class SelectTenantServiceTest {

    private TenantMembershipRepository memberships;
    private TenantRepository tenants;
    private TokenIssuer tokens;
    private SelectTenantService service;

    @BeforeEach
    void setUp() {
        memberships = Mockito.mock(TenantMembershipRepository.class);
        tenants = Mockito.mock(TenantRepository.class);
        tokens = Mockito.mock(TokenIssuer.class);
        service = new SelectTenantService(memberships, tenants, tokens);
    }

    @Test
    void 正常系_session_検証成功_membership_有り_tenant_ACTIVE_でアクセストークン発行() {
        when(tokens.verifySessionToken("session.jwt")).thenReturn(new UserId(100L));
        TenantId tenantId = new TenantId("acme");
        TenantMembership membership =
                new TenantMembership(
                        new UserId(100L),
                        tenantId,
                        "Acme",
                        "ja",
                        List.of(new RoleName("INVENTORY_MANAGER")),
                        List.of("LOC-1"),
                        List.of());
        when(memberships.findByUserAndTenant(new UserId(100L), tenantId))
                .thenReturn(Optional.of(membership));
        Tenant tenant =
                Tenant.restore(
                        tenantId,
                        "Acme",
                        TenantStatus.ACTIVE,
                        Instant.parse("2026-01-01T00:00:00Z"),
                        null,
                        "ja");
        when(tenants.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(tokens.issueAccessToken(eq(new UserId(100L)), eq(membership), any(Duration.class)))
                .thenReturn("access.jwt");

        SelectTenantUseCase.Result result =
                service.selectTenant(new SelectTenantUseCase.Command("session.jwt", "acme"));

        assertThat(result.accessToken()).isEqualTo("access.jwt");
        assertThat(result.expiresInSeconds()).isEqualTo(900L);
    }

    @Test
    void session_token_不正は認証失敗() {
        when(tokens.verifySessionToken("bad.jwt"))
                .thenThrow(new RuntimeException("signature mismatch"));

        assertThatThrownBy(
                        () ->
                                service.selectTenant(
                                        new SelectTenantUseCase.Command("bad.jwt", "acme")))
                .isInstanceOf(AuthenticationFailedException.class);

        verify(memberships, never()).findByUserAndTenant(any(), any());
        verify(tokens, never()).issueAccessToken(any(), any(), any());
    }

    @Test
    void membership_不在は_TenantAccessDenied() {
        when(tokens.verifySessionToken("session.jwt")).thenReturn(new UserId(100L));
        when(memberships.findByUserAndTenant(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                service.selectTenant(
                                        new SelectTenantUseCase.Command("session.jwt", "acme")))
                .isInstanceOf(TenantAccessDeniedException.class);

        verify(tenants, never()).findById(any());
        verify(tokens, never()).issueAccessToken(any(), any(), any());
    }

    @Test
    void tenant_DEACTIVATED_は_TenantAccessDenied() {
        when(tokens.verifySessionToken("session.jwt")).thenReturn(new UserId(100L));
        TenantId tenantId = new TenantId("acme");
        TenantMembership membership =
                new TenantMembership(
                        new UserId(100L),
                        tenantId,
                        "Acme",
                        "ja",
                        List.of(new RoleName("INVENTORY_MANAGER")),
                        List.of(),
                        List.of());
        when(memberships.findByUserAndTenant(new UserId(100L), tenantId))
                .thenReturn(Optional.of(membership));
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
                                service.selectTenant(
                                        new SelectTenantUseCase.Command("session.jwt", "acme")))
                .isInstanceOf(TenantAccessDeniedException.class);

        verify(tokens, never()).issueAccessToken(any(), any(), any());
    }

    @Test
    void membership_有るが_tenant_row_不在は_TenantAccessDenied_data_inconsistency_防御() {
        when(tokens.verifySessionToken("session.jwt")).thenReturn(new UserId(100L));
        TenantId tenantId = new TenantId("ghost");
        TenantMembership membership =
                new TenantMembership(
                        new UserId(100L),
                        tenantId,
                        "Ghost",
                        "ja",
                        List.of(new RoleName("INVENTORY_MANAGER")),
                        List.of(),
                        List.of());
        when(memberships.findByUserAndTenant(new UserId(100L), tenantId))
                .thenReturn(Optional.of(membership));
        when(tenants.findById(tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                service.selectTenant(
                                        new SelectTenantUseCase.Command("session.jwt", "ghost")))
                .isInstanceOf(TenantAccessDeniedException.class);

        verify(tokens, never()).issueAccessToken(any(), any(), any());
    }

    @Test
    void tenantId_形式違反は_TenantAccessDenied() {
        when(tokens.verifySessionToken("session.jwt")).thenReturn(new UserId(100L));

        assertThatThrownBy(
                        () ->
                                service.selectTenant(
                                        new SelectTenantUseCase.Command(
                                                "session.jwt", "INVALID UPPERCASE")))
                .isInstanceOf(TenantAccessDeniedException.class);

        verify(memberships, never()).findByUserAndTenant(any(), any());
    }
}
