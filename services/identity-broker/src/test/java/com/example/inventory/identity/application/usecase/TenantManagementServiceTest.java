package com.example.inventory.identity.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.dao.DuplicateKeyException;

import com.example.inventory.commons.tenant.TenantId;
import com.example.inventory.identity.application.port.in.RegisterTenantUseCase;
import com.example.inventory.identity.application.port.in.TenantAlreadyExistsException;
import com.example.inventory.identity.application.port.in.TenantNotFoundException;
import com.example.inventory.identity.application.port.out.TenantRepository;
import com.example.inventory.identity.domain.model.Tenant;
import com.example.inventory.identity.domain.model.TenantStatus;

class TenantManagementServiceTest {

    private static final Instant NOW = Instant.parse("2026-05-07T12:00:00Z");

    private TenantRepository repository;
    private TenantManagementService service;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(TenantRepository.class);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new TenantManagementService(repository, clock);
    }

    @Test
    void register_は_tenant_を_ACTIVE_で_append_する() {
        Tenant result = service.register(new RegisterTenantUseCase.Command("acme", "Acme Corp"));

        assertThat(result.tenantId().value()).isEqualTo("acme");
        assertThat(result.displayName()).isEqualTo("Acme Corp");
        assertThat(result.status()).isEqualTo(TenantStatus.ACTIVE);
        assertThat(result.createdAt()).isEqualTo(NOW);
        assertThat(result.deactivatedAt()).isNull();

        ArgumentCaptor<Tenant> captor = ArgumentCaptor.forClass(Tenant.class);
        verify(repository).append(captor.capture());
        assertThat(captor.getValue().tenantId().value()).isEqualTo("acme");
    }

    @Test
    void register_は_DuplicateKeyException_を_TenantAlreadyExistsException_に変換() {
        Mockito.doThrow(new DuplicateKeyException("dup")).when(repository).append(Mockito.any());

        assertThatThrownBy(
                        () ->
                                service.register(
                                        new RegisterTenantUseCase.Command("acme", "Acme Corp")))
                .isInstanceOf(TenantAlreadyExistsException.class);
    }

    @Test
    void register_は_tenantId_pattern_違反で_IllegalArgumentException() {
        assertThatThrownBy(() -> service.register(new RegisterTenantUseCase.Command("AB", "name")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deactivate_は_既存_ACTIVE_を_DEACTIVATED_に遷移して_update_を呼ぶ() {
        Tenant active =
                Tenant.restore(
                        new TenantId("acme"),
                        "Acme Corp",
                        TenantStatus.ACTIVE,
                        NOW.minusSeconds(86400),
                        null);
        when(repository.findById(new TenantId("acme"))).thenReturn(Optional.of(active));

        Tenant result = service.deactivate("acme");

        assertThat(result.status()).isEqualTo(TenantStatus.DEACTIVATED);
        assertThat(result.deactivatedAt()).isEqualTo(NOW);
        verify(repository).update(result);
    }

    @Test
    void deactivate_は_既に_DEACTIVATED_でも_冪等で_update_は呼ぶ() {
        Tenant deactivated =
                Tenant.restore(
                        new TenantId("acme"),
                        "Acme Corp",
                        TenantStatus.DEACTIVATED,
                        NOW.minusSeconds(86400),
                        NOW.minusSeconds(3600));
        when(repository.findById(new TenantId("acme"))).thenReturn(Optional.of(deactivated));

        Tenant result = service.deactivate("acme");

        // 冪等(状態維持、 deactivatedAt は最初に立った値のまま)
        assertThat(result.status()).isEqualTo(TenantStatus.DEACTIVATED);
        assertThat(result.deactivatedAt()).isEqualTo(NOW.minusSeconds(3600));
        verify(repository).update(result);
    }

    @Test
    void deactivate_は_該当無しなら_TenantNotFoundException() {
        when(repository.findById(new TenantId("nope"))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deactivate("nope"))
                .isInstanceOf(TenantNotFoundException.class);
        verify(repository, never()).update(Mockito.any());
    }

    @Test
    void get_は_該当無しなら_TenantNotFoundException() {
        when(repository.findById(new TenantId("nope"))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get("nope")).isInstanceOf(TenantNotFoundException.class);
    }

    @Test
    void listAll_は_repository_の_findAll_を返す() {
        Tenant t =
                Tenant.restore(
                        new TenantId("acme"),
                        "Acme",
                        TenantStatus.ACTIVE,
                        NOW.minusSeconds(86400),
                        null);
        when(repository.findAll()).thenReturn(java.util.List.of(t));

        assertThat(service.listAll()).containsExactly(t);
    }
}
