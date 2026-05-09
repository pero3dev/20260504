package com.example.inventory.identity.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.example.inventory.identity.application.port.in.UserNotFoundException;
import com.example.inventory.identity.application.port.out.UserRepository;
import com.example.inventory.identity.domain.model.PasswordHash;
import com.example.inventory.identity.domain.model.User;
import com.example.inventory.identity.domain.model.UserEmail;
import com.example.inventory.identity.domain.model.UserId;

class UserManagementServiceTest {

    private UserRepository repository;
    private UserManagementService service;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(UserRepository.class);
        service = new UserManagementService(repository);
    }

    @Test
    void get_は_該当無しなら_UserNotFoundException() {
        Mockito.when(repository.findById(new UserId(42L))).thenReturn(Optional.empty());

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
        Mockito.when(repository.findById(new UserId(42L))).thenReturn(Optional.of(u));

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
        Mockito.when(repository.findAll()).thenReturn(List.of(a, b));

        List<User> all = service.listAll();

        assertThat(all).hasSize(2);
        assertThat(all).extracting(u -> u.id().value()).containsExactly(1L, 2L);
    }
}
