package com.example.inventory.identity.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import com.example.inventory.commons.persistence.SnowflakeIdGenerator;
import com.example.inventory.commons.security.RevocationStore;
import com.example.inventory.commons.tenant.TenantId;
import com.example.inventory.identity.application.port.in.AddUserMembershipUseCase;
import com.example.inventory.identity.application.port.in.RegisterUserUseCase;
import com.example.inventory.identity.application.port.in.RemoveUserMembershipUseCase;
import com.example.inventory.identity.application.port.in.TenantNotFoundException;
import com.example.inventory.identity.application.port.in.UserAlreadyExistsException;
import com.example.inventory.identity.application.port.in.UserMembershipAlreadyExistsException;
import com.example.inventory.identity.application.port.in.UserMembershipNotFoundException;
import com.example.inventory.identity.application.port.in.UserNotFoundException;
import com.example.inventory.identity.application.port.out.TenantMembershipRepository;
import com.example.inventory.identity.application.port.out.TenantRepository;
import com.example.inventory.identity.application.port.out.UserRepository;
import com.example.inventory.identity.domain.model.PasswordHash;
import com.example.inventory.identity.domain.model.Tenant;
import com.example.inventory.identity.domain.model.TenantMembership;
import com.example.inventory.identity.domain.model.TenantStatus;
import com.example.inventory.identity.domain.model.User;
import com.example.inventory.identity.domain.model.UserEmail;
import com.example.inventory.identity.domain.model.UserId;
import com.example.inventory.identity.domain.model.UserStatus;

class UserManagementServiceTest {

    private static final Instant NOW = Instant.parse("2026-05-10T12:00:00Z");

    private UserRepository userRepository;
    private TenantRepository tenantRepository;
    private TenantMembershipRepository membershipRepository;
    private SnowflakeIdGenerator idGenerator;
    private RevocationStore revocationStore;
    private UserManagementService service;

    @BeforeEach
    void setUp() {
        userRepository = Mockito.mock(UserRepository.class);
        tenantRepository = Mockito.mock(TenantRepository.class);
        membershipRepository = Mockito.mock(TenantMembershipRepository.class);
        idGenerator = Mockito.mock(SnowflakeIdGenerator.class);
        revocationStore = Mockito.mock(RevocationStore.class);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service =
                new UserManagementService(
                        userRepository,
                        tenantRepository,
                        membershipRepository,
                        idGenerator,
                        clock,
                        revocationStore);
    }

    private static Tenant activeTenant(String id) {
        return Tenant.restore(
                new TenantId(id),
                "Display " + id,
                TenantStatus.ACTIVE,
                Instant.parse("2026-01-01T00:00:00Z"),
                null,
                "ja");
    }

    @Test
    void get_は_該当無しなら_UserNotFoundException() {
        when(userRepository.findById(new UserId(42L))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(42L)).isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void get_は_該当_user_を返す() {
        User u =
                User.restore(
                        new UserId(42L),
                        new UserEmail("alice@example.com"),
                        new PasswordHash("$2a$10$abc"),
                        "Alice",
                        0L);
        when(userRepository.findById(new UserId(42L))).thenReturn(Optional.of(u));

        User result = service.get(42L);

        assertThat(result.id().value()).isEqualTo(42L);
        assertThat(result.email().value()).isEqualTo("alice@example.com");
    }

    @Test
    void listAll_は_repository_の_findAll_を返す() {
        User a =
                User.restore(
                        new UserId(1L),
                        new UserEmail("a@example.com"),
                        new PasswordHash("$2a$10$x"),
                        "A",
                        0L);
        User b =
                User.restore(
                        new UserId(2L),
                        new UserEmail("b@example.com"),
                        new PasswordHash("$2a$10$y"),
                        "B",
                        0L);
        when(userRepository.findAll()).thenReturn(List.of(a, b));

        List<User> all = service.listAll();

        assertThat(all).hasSize(2);
        assertThat(all).extracting(u -> u.id().value()).containsExactly(1L, 2L);
    }

    @Test
    void register_は_新規ユーザを_federation_only_で作成し_membership_も付ける() {
        when(tenantRepository.findById(new TenantId("acme")))
                .thenReturn(Optional.of(activeTenant("acme")));
        when(userRepository.findByEmail(new UserEmail("bob@example.com")))
                .thenReturn(Optional.empty());
        when(idGenerator.nextId()).thenReturn(900100L);

        User result =
                service.register(
                        new RegisterUserUseCase.Command(
                                "bob@example.com", "Bob", "acme", "INVENTORY_MANAGER"));

        assertThat(result.id().value()).isEqualTo(900100L);
        assertThat(result.email().value()).isEqualTo("bob@example.com");
        assertThat(result.passwordHash().value())
                .isEqualTo(UserManagementService.FEDERATED_PASSWORD_HASH_SENTINEL);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().displayName()).isEqualTo("Bob");

        ArgumentCaptor<TenantMembership> mCaptor = ArgumentCaptor.forClass(TenantMembership.class);
        verify(membershipRepository).add(mCaptor.capture());
        TenantMembership added = mCaptor.getValue();
        assertThat(added.userId().value()).isEqualTo(900100L);
        assertThat(added.tenantId().value()).isEqualTo("acme");
        assertThat(added.tenantDisplayName()).isEqualTo("Display acme");
        assertThat(added.tenantLocale()).isEqualTo("ja");
        assertThat(added.roleNames()).containsExactly("INVENTORY_MANAGER");
    }

    @Test
    void register_は_email_既存なら_UserAlreadyExistsException() {
        when(tenantRepository.findById(new TenantId("acme")))
                .thenReturn(Optional.of(activeTenant("acme")));
        User existing =
                User.restore(
                        new UserId(1L),
                        new UserEmail("bob@example.com"),
                        new PasswordHash("$2a$10$..."),
                        "Bob",
                        0L);
        when(userRepository.findByEmail(new UserEmail("bob@example.com")))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(
                        () ->
                                service.register(
                                        new RegisterUserUseCase.Command(
                                                "bob@example.com", "Bob", "acme", "VIEWER")))
                .isInstanceOf(UserAlreadyExistsException.class);

        verify(userRepository, never()).save(any());
        verify(membershipRepository, never()).add(any());
    }

    @Test
    void register_は_tenant_不在なら_TenantNotFoundException() {
        when(tenantRepository.findById(new TenantId("ghost"))).thenReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                service.register(
                                        new RegisterUserUseCase.Command(
                                                "bob@example.com", "Bob", "ghost", "VIEWER")))
                .isInstanceOf(TenantNotFoundException.class);

        verify(userRepository, never()).findByEmail(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_は_tenant_DEACTIVATED_なら_TenantNotFoundException() {
        Tenant deactivated =
                Tenant.restore(
                        new TenantId("acme"),
                        "Acme",
                        TenantStatus.DEACTIVATED,
                        Instant.parse("2026-01-01T00:00:00Z"),
                        Instant.parse("2026-04-01T00:00:00Z"),
                        "ja");
        when(tenantRepository.findById(new TenantId("acme"))).thenReturn(Optional.of(deactivated));

        assertThatThrownBy(
                        () ->
                                service.register(
                                        new RegisterUserUseCase.Command(
                                                "bob@example.com", "Bob", "acme", "VIEWER")))
                .isInstanceOf(TenantNotFoundException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void register_は_email_format_不正なら_IllegalArgumentException() {
        assertThatThrownBy(
                        () ->
                                service.register(
                                        new RegisterUserUseCase.Command(
                                                "not-an-email", "Bob", "acme", "VIEWER")))
                .isInstanceOf(IllegalArgumentException.class);

        verify(tenantRepository, never()).findById(any());
    }

    // ---------- addMembership ----------

    @Test
    void addMembership_は_既存_user_に_新規_membership_を作成する() {
        User existing =
                User.restore(
                        new UserId(900100L),
                        new UserEmail("alice@example.com"),
                        new PasswordHash("$2a$10$..."),
                        "Alice",
                        0L);
        when(userRepository.findById(new UserId(900100L))).thenReturn(Optional.of(existing));
        when(tenantRepository.findById(new TenantId("acme")))
                .thenReturn(Optional.of(activeTenant("acme")));
        when(membershipRepository.findByUserAndTenant(new UserId(900100L), new TenantId("acme")))
                .thenReturn(Optional.empty());

        TenantMembership result =
                service.addMembership(
                        new AddUserMembershipUseCase.Command(900100L, "acme", "INVENTORY_MANAGER"));

        assertThat(result.userId().value()).isEqualTo(900100L);
        assertThat(result.tenantId().value()).isEqualTo("acme");
        assertThat(result.tenantDisplayName()).isEqualTo("Display acme");
        assertThat(result.tenantLocale()).isEqualTo("ja");
        assertThat(result.roleNames()).containsExactly("INVENTORY_MANAGER");

        verify(membershipRepository).add(any());
    }

    @Test
    void addMembership_は_user_不在なら_UserNotFoundException() {
        when(userRepository.findById(new UserId(99L))).thenReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                service.addMembership(
                                        new AddUserMembershipUseCase.Command(
                                                99L, "acme", "VIEWER")))
                .isInstanceOf(UserNotFoundException.class);

        verify(tenantRepository, never()).findById(any());
        verify(membershipRepository, never()).add(any());
    }

    @Test
    void addMembership_は_tenant_不在なら_TenantNotFoundException() {
        User existing =
                User.restore(
                        new UserId(900100L),
                        new UserEmail("alice@example.com"),
                        new PasswordHash("$2a$10$..."),
                        "Alice",
                        0L);
        when(userRepository.findById(new UserId(900100L))).thenReturn(Optional.of(existing));
        when(tenantRepository.findById(new TenantId("ghost"))).thenReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                service.addMembership(
                                        new AddUserMembershipUseCase.Command(
                                                900100L, "ghost", "VIEWER")))
                .isInstanceOf(TenantNotFoundException.class);

        verify(membershipRepository, never()).findByUserAndTenant(any(), any());
        verify(membershipRepository, never()).add(any());
    }

    // ---------- deactivate (user 全体) ----------

    @Test
    void deactivate_は_既存_ACTIVE_user_を_DEACTIVATED_に遷移() {
        User active =
                User.restore(
                        new UserId(900100L),
                        new UserEmail("alice@example.com"),
                        new PasswordHash("$2a$10$..."),
                        "Alice",
                        0L);
        when(userRepository.findById(new UserId(900100L))).thenReturn(Optional.of(active));
        when(userRepository.update(any())).thenReturn(1);

        User result = service.deactivate(900100L);

        assertThat(result.status()).isEqualTo(UserStatus.DEACTIVATED);
        assertThat(result.deactivatedAt()).isEqualTo(NOW);
        verify(userRepository).update(result);
        verify(revocationStore).revokeUser(900100L, Duration.ofMinutes(15));
    }

    @Test
    void deactivate_は_既に_DEACTIVATED_でも_冪等で_update_は呼ぶ() {
        User deactivated =
                User.restore(
                        new UserId(900100L),
                        new UserEmail("alice@example.com"),
                        new PasswordHash("$2a$10$..."),
                        "Alice",
                        0L,
                        UserStatus.DEACTIVATED,
                        NOW.minusSeconds(3600));
        when(userRepository.findById(new UserId(900100L))).thenReturn(Optional.of(deactivated));
        when(userRepository.update(any())).thenReturn(1);

        User result = service.deactivate(900100L);

        // 冪等(state 維持、 deactivatedAt は最初に立った値のまま)
        assertThat(result.status()).isEqualTo(UserStatus.DEACTIVATED);
        assertThat(result.deactivatedAt()).isEqualTo(NOW.minusSeconds(3600));
        verify(userRepository).update(result);
    }

    @Test
    void deactivate_は_該当無しなら_UserNotFoundException() {
        when(userRepository.findById(new UserId(99L))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deactivate(99L)).isInstanceOf(UserNotFoundException.class);
        verify(userRepository, never()).update(any());
    }

    // ---------- removeMembership ----------

    @Test
    void removeMembership_は_該当行を削除し_user_を_revoke_する() {
        when(membershipRepository.delete(new UserId(900100L), new TenantId("acme"))).thenReturn(1);

        service.removeMembership(new RemoveUserMembershipUseCase.Command(900100L, "acme"));

        verify(membershipRepository).delete(new UserId(900100L), new TenantId("acme"));
        verify(revocationStore).revokeUser(900100L, Duration.ofMinutes(15));
    }

    @Test
    void removeMembership_は_該当無しなら_UserMembershipNotFoundException() {
        when(membershipRepository.delete(new UserId(99L), new TenantId("acme"))).thenReturn(0);

        assertThatThrownBy(
                        () ->
                                service.removeMembership(
                                        new RemoveUserMembershipUseCase.Command(99L, "acme")))
                .isInstanceOf(UserMembershipNotFoundException.class);
    }

    @Test
    void removeMembership_は_tenantId_形式不正なら_IllegalArgumentException() {
        assertThatThrownBy(
                        () ->
                                service.removeMembership(
                                        new RemoveUserMembershipUseCase.Command(
                                                900100L, "INVALID UPPERCASE")))
                .isInstanceOf(IllegalArgumentException.class);

        verify(membershipRepository, never()).delete(any(), any());
    }

    @Test
    void addMembership_は_既存_membership_なら_UserMembershipAlreadyExistsException() {
        User existing =
                User.restore(
                        new UserId(900100L),
                        new UserEmail("alice@example.com"),
                        new PasswordHash("$2a$10$..."),
                        "Alice",
                        0L);
        TenantMembership existingMembership =
                new TenantMembership(
                        new UserId(900100L),
                        new TenantId("acme"),
                        "Acme",
                        "ja",
                        java.util.List.of(
                                new com.example.inventory.identity.domain.model.RoleName("VIEWER")),
                        java.util.List.of(),
                        java.util.List.of());
        when(userRepository.findById(new UserId(900100L))).thenReturn(Optional.of(existing));
        when(tenantRepository.findById(new TenantId("acme")))
                .thenReturn(Optional.of(activeTenant("acme")));
        when(membershipRepository.findByUserAndTenant(new UserId(900100L), new TenantId("acme")))
                .thenReturn(Optional.of(existingMembership));

        assertThatThrownBy(
                        () ->
                                service.addMembership(
                                        new AddUserMembershipUseCase.Command(
                                                900100L, "acme", "INVENTORY_MANAGER")))
                .isInstanceOf(UserMembershipAlreadyExistsException.class);

        verify(membershipRepository, never()).add(any());
    }
}
