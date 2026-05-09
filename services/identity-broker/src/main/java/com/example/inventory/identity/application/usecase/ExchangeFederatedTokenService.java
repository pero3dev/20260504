package com.example.inventory.identity.application.usecase;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.inventory.commons.audit.Auditable;
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
import com.example.inventory.identity.domain.model.UserStatus;

/**
 * 外部 IdP の access token を Identity Broker のセッショントークンへ交換する usecase(F2 phase C)。
 *
 * <p>Cognito の access token は `username` claim に email を載せる構成を想定。 取り出した email で内部 {@link User}
 * を検索し、 ヒットしない場合の挙動は {@link FederationJitProperties#enabled()} で分岐する:
 *
 * <ul>
 *   <li>JIT 無効(default): {@link AuthenticationFailedException} を返す
 *   <li>JIT 有効: default tenant + default role で User + TenantMembership を自動作成(SAML JIT
 *       provisioning)
 * </ul>
 *
 * <p>列挙攻撃対策のため、 token 不正 / subject 形式違反 / JIT 失敗(設定不備 / tenant 不在 / DEACTIVATED)は 全て同じ 401 として扱う。
 * server log には判別可能な情報を残す。
 *
 * <p>{@code @Auditable} で成功 / 失敗を `audit.log.v1` へ記録(J-SOX 統制、 platform tenant)。
 */
@Service
public class ExchangeFederatedTokenService implements ExchangeFederatedTokenUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(ExchangeFederatedTokenService.class);
    private static final Duration SESSION_TOKEN_TTL = Duration.ofMinutes(5);

    /** SAML JIT で作成する User の password_hash sentinel。 BCrypt 形式ではないので password 認証では決して通らない。 */
    static final String FEDERATED_PASSWORD_HASH_SENTINEL = "$external_federation$";

    private final IdpTokenVerifier idpTokenVerifier;
    private final UserRepository userRepository;
    private final TenantMembershipRepository membershipRepository;
    private final TenantRepository tenantRepository;
    private final TokenIssuer tokenIssuer;
    private final SnowflakeIdGenerator idGenerator;
    private final FederationJitProperties jit;

    public ExchangeFederatedTokenService(
            IdpTokenVerifier idpTokenVerifier,
            UserRepository userRepository,
            TenantMembershipRepository membershipRepository,
            TenantRepository tenantRepository,
            TokenIssuer tokenIssuer,
            SnowflakeIdGenerator idGenerator,
            FederationJitProperties jit) {
        this.idpTokenVerifier = idpTokenVerifier;
        this.userRepository = userRepository;
        this.membershipRepository = membershipRepository;
        this.tenantRepository = tenantRepository;
        this.tokenIssuer = tokenIssuer;
        this.idGenerator = idGenerator;
        this.jit = jit;
    }

    @Override
    @Transactional
    @Auditable(
            action = "FEDERATED_TOKEN_EXCHANGE",
            targetType = "FederatedSession",
            targetIdExpression = "'federated'")
    public Result exchange(Command command) {
        IdpTokenVerifier.Subject subject;
        try {
            subject = idpTokenVerifier.verify(command.providerAccessToken());
        } catch (InvalidIdpTokenException e) {
            LOG.info("federated token 検証失敗 reason={}", e.getMessage());
            throw new AuthenticationFailedException();
        }

        UserEmail email = parseEmailOrThrow(subject);
        User user = userRepository.findByEmail(email).orElseGet(() -> jitProvision(email, subject));

        if (user.status() == UserStatus.DEACTIVATED) {
            // 列挙対策のため通常の 401 と同じ例外。 federated 経路でも DEACTIVATED 既存 user を新セッションで甦らせない。
            LOG.info(
                    "federated 認証失敗 reason=user-deactivated issuer={} userId={}",
                    subject.issuer(),
                    user.id().value());
            throw new AuthenticationFailedException();
        }

        List<TenantMembership> memberships = membershipRepository.findByUserId(user.id());
        if (memberships.isEmpty()) {
            // JIT 経路で User だけ作成され membership が無い状態は基本起きないが、 過去 batch
            // 不整合等で User 単独だと session に乗せる tenant が無く実質 401 と同じ。
            LOG.info(
                    "federated 認証成功するも accessibleTenants=0 issuer={} userId={}",
                    subject.issuer(),
                    user.id().value());
            throw new AuthenticationFailedException();
        }
        List<TenantId> tenantIds = memberships.stream().map(TenantMembership::tenantId).toList();

        String sessionToken =
                tokenIssuer.issueSessionToken(user.id(), tenantIds, SESSION_TOKEN_TTL);
        LOG.info(
                "federated 認証成功 issuer={} userId={} tenantsCount={}",
                subject.issuer(),
                user.id().value(),
                memberships.size());
        return new Result(sessionToken, SESSION_TOKEN_TTL.toSeconds(), memberships);
    }

    /**
     * 内部 User 不在時の JIT provisioning(または列挙攻撃対策の 401)。
     *
     * <p>JIT 無効 / 設定不備 / tenant 不在 / tenant DEACTIVATED は全て {@link AuthenticationFailedException}。
     * ログには分類可能な情報を残す。
     */
    private User jitProvision(UserEmail email, IdpTokenVerifier.Subject subject) {
        if (!jit.enabled()) {
            LOG.info(
                    "federated 認証成功するも内部 User 不在 (JIT 無効) issuer={} subject(masked)={}",
                    subject.issuer(),
                    mask(subject.value()));
            throw new AuthenticationFailedException();
        }
        if (jit.defaultTenantId() == null || jit.defaultTenantId().isBlank()) {
            LOG.warn("JIT が enabled だが default-tenant-id 未設定 issuer={}", subject.issuer());
            throw new AuthenticationFailedException();
        }
        TenantId tenantId = new TenantId(jit.defaultTenantId());
        Tenant tenant =
                tenantRepository
                        .findById(tenantId)
                        .orElseThrow(
                                () -> {
                                    LOG.warn(
                                            "JIT default tenant が DB 不在 tenantId={}",
                                            tenantId.value());
                                    return new AuthenticationFailedException();
                                });
        if (tenant.status() == TenantStatus.DEACTIVATED) {
            LOG.warn("JIT default tenant が DEACTIVATED tenantId={}", tenantId.value());
            throw new AuthenticationFailedException();
        }

        UserId newUserId = new UserId(idGenerator.nextId());
        User newUser =
                User.create(
                        newUserId,
                        email,
                        new PasswordHash(FEDERATED_PASSWORD_HASH_SENTINEL),
                        deriveDisplayName(email));
        userRepository.save(newUser);
        String resolvedRole = resolveRole(subject);
        TenantMembership membership =
                new TenantMembership(
                        newUserId,
                        tenantId,
                        tenant.displayName(),
                        tenant.locale(),
                        List.of(new RoleName(resolvedRole)),
                        List.of(),
                        List.of());
        membershipRepository.add(membership);
        LOG.info(
                "JIT provisioning 成功 issuer={} newUserId={} tenantId={} role={} groupsMatched={}",
                subject.issuer(),
                newUserId.value(),
                tenantId.value(),
                resolvedRole,
                resolvedRole.equals(jit.defaultRole()) ? "<default>" : "<group>");
        return newUser;
    }

    /**
     * IdP groups claim を順に見て {@link FederationJitProperties#groupRoleMappings} の最初の一致を採る。 一致無し /
     * groups 空 / mapping 未設定 はいずれも {@code defaultRole} に fallback。
     */
    private String resolveRole(IdpTokenVerifier.Subject subject) {
        Map<String, String> mappings = jit.groupRoleMappings();
        if (mappings.isEmpty() || subject.groups().isEmpty()) {
            return jit.defaultRole();
        }
        for (String group : subject.groups()) {
            String role = mappings.get(group);
            if (role != null && !role.isBlank()) {
                return role;
            }
        }
        return jit.defaultRole();
    }

    private static UserEmail parseEmailOrThrow(IdpTokenVerifier.Subject subject) {
        try {
            return new UserEmail(subject.value());
        } catch (IllegalArgumentException e) {
            // subject claim が email 形式でない構成は本 phase 未対応(将来 sub claim 直引き等)
            throw new AuthenticationFailedException();
        }
    }

    /** email の local part(@ より前)を displayName の初期値にする。 SAML attribute 連携は将来。 */
    private static String deriveDisplayName(UserEmail email) {
        String value = email.value();
        int at = value.indexOf('@');
        return at <= 0 ? value : value.substring(0, at);
    }

    private static String mask(String value) {
        if (value == null || value.length() <= 2) return "***";
        int at = value.indexOf('@');
        if (at <= 1) return value.charAt(0) + "***";
        return value.charAt(0) + "***" + value.substring(at);
    }
}
