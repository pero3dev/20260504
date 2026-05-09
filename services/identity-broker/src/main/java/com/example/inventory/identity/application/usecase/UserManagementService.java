package com.example.inventory.identity.application.usecase;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.inventory.commons.audit.Auditable;
import com.example.inventory.identity.application.port.in.GetUserUseCase;
import com.example.inventory.identity.application.port.in.UserNotFoundException;
import com.example.inventory.identity.application.port.out.UserRepository;
import com.example.inventory.identity.domain.model.User;
import com.example.inventory.identity.domain.model.UserId;

/**
 * 管理者向け user 参照ユースケース(A5 follow-up³)。
 *
 * <p>read-only MVP。 write 系(register / deactivate / link membership)は password vs. federation-only
 * provisioning の ADR が要るため次 phase に分離。
 *
 * <p><b>監査:</b> {@code /v1/admin/users/*} は J-SOX 上の重要統制点(管理者の user 参照行為自体が統制対象)で、 全 read メソッドに
 * {@link Auditable} を {@code read = true} で付与する。
 */
@Service
public class UserManagementService implements GetUserUseCase {

    private final UserRepository repository;

    public UserManagementService(UserRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    @Auditable(
            action = "USER_GET",
            targetType = "User",
            targetIdExpression = "#userId",
            read = true)
    public User get(long userId) {
        return repository
                .findById(new UserId(userId))
                .orElseThrow(() -> new UserNotFoundException(userId));
    }

    @Override
    @Transactional(readOnly = true)
    @Auditable(action = "USER_LIST_ALL", targetType = "User", read = true)
    public List<User> listAll() {
        return repository.findAll();
    }
}
