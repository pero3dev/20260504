package com.example.inventory.identity.application.usecase;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.inventory.commons.audit.Auditable;
import com.example.inventory.commons.persistence.SnowflakeIdGenerator;
import com.example.inventory.commons.tenant.TenantId;
import com.example.inventory.identity.application.port.in.AddUserMembershipUseCase;
import com.example.inventory.identity.application.port.in.GetUserUseCase;
import com.example.inventory.identity.application.port.in.RegisterUserUseCase;
import com.example.inventory.identity.application.port.in.TenantNotFoundException;
import com.example.inventory.identity.application.port.in.UserAlreadyExistsException;
import com.example.inventory.identity.application.port.in.UserMembershipAlreadyExistsException;
import com.example.inventory.identity.application.port.in.UserNotFoundException;
import com.example.inventory.identity.application.port.out.TenantMembershipRepository;
import com.example.inventory.identity.application.port.out.TenantRepository;
import com.example.inventory.identity.application.port.out.UserRepository;
import com.example.inventory.identity.domain.model.PasswordHash;
import com.example.inventory.identity.domain.model.RoleName;
import com.example.inventory.identity.domain.model.Tenant;
import com.example.inventory.identity.domain.model.TenantMembership;
import com.example.inventory.identity.domain.model.TenantStatus;
import com.example.inventory.identity.domain.model.User;
import com.example.inventory.identity.domain.model.UserEmail;
import com.example.inventory.identity.domain.model.UserId;

/**
 * 管理者向け user ユースケース。 read-only(A5 follow-up³)+ federation-only 登録(A5 follow-up¹²)。
 *
 * <p>本 phase で実装する {@link #register} は federation-only(password 不要)。 SAML 連携で同 email が来たときに 既存 user
 * として認識され、 JIT 経路を踏まずに通常ログイン flow になる。 password 認証経路は password sentinel( {@code
 * $external_federation$})で物理的に通れない。
 *
 * <p><b>監査:</b> {@code /v1/admin/users/*} は J-SOX 上の重要統制点で、 全メソッドに {@link Auditable} を付与。 read 系は
 * {@code read = true} で参照行為も audit。
 */
@Service
public class UserManagementService
        implements GetUserUseCase, RegisterUserUseCase, AddUserMembershipUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(UserManagementService.class);

    /** federation-only user の password_hash sentinel。 BCrypt 形式ではないので password 認証では決して通らない。 */
    static final String FEDERATED_PASSWORD_HASH_SENTINEL = "$external_federation$";

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final TenantMembershipRepository membershipRepository;
    private final SnowflakeIdGenerator idGenerator;

    public UserManagementService(
            UserRepository userRepository,
            TenantRepository tenantRepository,
            TenantMembershipRepository membershipRepository,
            SnowflakeIdGenerator idGenerator) {
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
        this.membershipRepository = membershipRepository;
        this.idGenerator = idGenerator;
    }

    @Override
    @Transactional(readOnly = true)
    @Auditable(
            action = "USER_GET",
            targetType = "User",
            targetIdExpression = "#userId",
            read = true)
    public User get(long userId) {
        return userRepository
                .findById(new UserId(userId))
                .orElseThrow(() -> new UserNotFoundException(userId));
    }

    @Override
    @Transactional(readOnly = true)
    @Auditable(action = "USER_LIST_ALL", targetType = "User", read = true)
    public List<User> listAll() {
        return userRepository.findAll();
    }

    @Override
    @Transactional
    @Auditable(action = "USER_REGISTER", targetType = "User", targetIdExpression = "#command.email")
    public User register(RegisterUserUseCase.Command command) {
        UserEmail email = new UserEmail(command.email());
        TenantId tenantId = new TenantId(command.tenantId());
        RoleName role = new RoleName(command.roleName());

        Tenant tenant =
                tenantRepository
                        .findById(tenantId)
                        .orElseThrow(() -> new TenantNotFoundException(command.tenantId()));
        if (tenant.status() == TenantStatus.DEACTIVATED) {
            // DEACTIVATED tenant への新規 membership は意味が無く SelectTenantService でも弾かれる。 admin に明示的に伝える。
            throw new TenantNotFoundException(command.tenantId());
        }
        if (userRepository.findByEmail(email).isPresent()) {
            throw new UserAlreadyExistsException(command.email());
        }

        UserId newUserId = new UserId(idGenerator.nextId());
        User newUser =
                User.create(
                        newUserId,
                        email,
                        new PasswordHash(FEDERATED_PASSWORD_HASH_SENTINEL),
                        command.displayName() == null ? "" : command.displayName());
        try {
            userRepository.save(newUser);
        } catch (DuplicateKeyException e) {
            // findByEmail と save の間で並列 INSERT が発生した稀ケース。 409 で同じ扱い。
            throw new UserAlreadyExistsException(command.email());
        }

        TenantMembership membership =
                new TenantMembership(
                        newUserId,
                        tenantId,
                        tenant.displayName(),
                        tenant.locale(),
                        List.of(role),
                        List.of(),
                        List.of());
        membershipRepository.add(membership);

        LOG.info(
                "user 登録完了 (federation-only) userId={} email={} tenantId={} role={}",
                newUserId.value(),
                email.value(),
                tenantId.value(),
                role.value());
        return newUser;
    }

    @Override
    @Transactional
    @Auditable(
            action = "USER_MEMBERSHIP_ADD",
            targetType = "TenantMembership",
            targetIdExpression = "#command.userId + '/' + #command.tenantId")
    public TenantMembership addMembership(AddUserMembershipUseCase.Command command) {
        UserId userId = new UserId(command.userId());
        TenantId tenantId = new TenantId(command.tenantId());
        RoleName role = new RoleName(command.roleName());

        // user 不在は 404。 race で findById 後に削除された場合の整合性は本 phase 範囲外。
        userRepository
                .findById(userId)
                .orElseThrow(() -> new UserNotFoundException(command.userId()));

        Tenant tenant =
                tenantRepository
                        .findById(tenantId)
                        .orElseThrow(() -> new TenantNotFoundException(command.tenantId()));
        if (tenant.status() == TenantStatus.DEACTIVATED) {
            throw new TenantNotFoundException(command.tenantId());
        }

        if (membershipRepository.findByUserAndTenant(userId, tenantId).isPresent()) {
            throw new UserMembershipAlreadyExistsException(command.userId(), command.tenantId());
        }

        TenantMembership membership =
                new TenantMembership(
                        userId,
                        tenantId,
                        tenant.displayName(),
                        tenant.locale(),
                        List.of(role),
                        List.of(),
                        List.of());
        try {
            membershipRepository.add(membership);
        } catch (DuplicateKeyException e) {
            // findByUserAndTenant と add の間で並列 INSERT があった稀ケース。 409 で同じ扱い。
            throw new UserMembershipAlreadyExistsException(command.userId(), command.tenantId());
        }

        LOG.info(
                "user membership 追加 userId={} tenantId={} role={}",
                userId.value(),
                tenantId.value(),
                role.value());
        return membership;
    }
}
