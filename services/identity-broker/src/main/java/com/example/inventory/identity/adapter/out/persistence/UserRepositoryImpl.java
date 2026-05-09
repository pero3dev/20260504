package com.example.inventory.identity.adapter.out.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.example.inventory.identity.application.port.out.UserRepository;
import com.example.inventory.identity.domain.model.PasswordHash;
import com.example.inventory.identity.domain.model.User;
import com.example.inventory.identity.domain.model.UserEmail;
import com.example.inventory.identity.domain.model.UserId;
import com.example.inventory.identity.domain.model.UserStatus;

@Repository
public class UserRepositoryImpl implements UserRepository {

    private final UserMapper mapper;

    public UserRepositoryImpl(UserMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<User> findByEmail(UserEmail email) {
        UserRow row = mapper.findByEmail(email.value());
        return row == null ? Optional.empty() : Optional.of(toDomain(row));
    }

    @Override
    public Optional<User> findById(UserId id) {
        UserRow row = mapper.findById(id.value());
        return row == null ? Optional.empty() : Optional.of(toDomain(row));
    }

    @Override
    public List<User> findAll() {
        return mapper.findAll().stream().map(UserRepositoryImpl::toDomain).toList();
    }

    @Override
    public void save(User user) {
        mapper.insert(toRow(user));
    }

    @Override
    public int update(User user) {
        return mapper.update(toRow(user));
    }

    private static UserRow toRow(User user) {
        return new UserRow(
                user.id().value(),
                user.email().value(),
                user.passwordHash().value(),
                user.displayName(),
                user.version(),
                user.status().name(),
                user.deactivatedAt());
    }

    private static User toDomain(UserRow row) {
        UserStatus status =
                row.status() == null ? UserStatus.ACTIVE : UserStatus.valueOf(row.status());
        return User.restore(
                new UserId(row.id()),
                new UserEmail(row.email()),
                new PasswordHash(row.passwordHash()),
                row.displayName(),
                row.version(),
                status,
                row.deactivatedAt());
    }
}
