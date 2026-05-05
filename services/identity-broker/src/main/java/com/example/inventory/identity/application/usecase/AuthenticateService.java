package com.example.inventory.identity.application.usecase;

import java.time.Duration;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.inventory.commons.audit.Auditable;
import com.example.inventory.commons.tenant.TenantId;
import com.example.inventory.identity.application.port.in.AuthenticateUseCase;
import com.example.inventory.identity.application.port.in.AuthenticationFailedException;
import com.example.inventory.identity.application.port.out.PasswordVerifier;
import com.example.inventory.identity.application.port.out.TenantMembershipRepository;
import com.example.inventory.identity.application.port.out.TokenIssuer;
import com.example.inventory.identity.application.port.out.UserRepository;
import com.example.inventory.identity.domain.model.TenantMembership;
import com.example.inventory.identity.domain.model.User;
import com.example.inventory.identity.domain.model.UserEmail;

/**
 * クレデンシャル認証ユースケース。
 *
 * <p>{@code @Auditable} で成功・失敗を {@code audit.log.v1} へ記録する(J-SOX 統制)。 テナント未確立のため AuditEventEmitter
 * が {@link TenantId#SYSTEM}({@code "platform"}) にフォールバックして発行する。
 *
 * <p>メール存在しない / パスワード不一致 / その他の認証失敗は同じ {@link AuthenticationFailedException} を投げる(ユーザー列挙攻撃を防ぐ)。
 */
@Service
public class AuthenticateService implements AuthenticateUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(AuthenticateService.class);
    private static final Duration SESSION_TOKEN_TTL = Duration.ofMinutes(5);

    private final UserRepository userRepository;
    private final TenantMembershipRepository membershipRepository;
    private final PasswordVerifier passwordVerifier;
    private final TokenIssuer tokenIssuer;

    public AuthenticateService(
            UserRepository userRepository,
            TenantMembershipRepository membershipRepository,
            PasswordVerifier passwordVerifier,
            TokenIssuer tokenIssuer) {
        this.userRepository = userRepository;
        this.membershipRepository = membershipRepository;
        this.passwordVerifier = passwordVerifier;
        this.tokenIssuer = tokenIssuer;
    }

    @Override
    @Auditable(
            action = "USER_AUTHENTICATE",
            targetType = "User",
            targetIdExpression = "#command.email")
    public Result authenticate(Command command) {
        UserEmail email = parseEmail(command.email());
        User user =
                userRepository
                        .findByEmail(email)
                        .orElseThrow(
                                () -> {
                                    LOG.info(
                                            "認証失敗 reason=user-not-found email={}",
                                            maskedEmail(command.email()));
                                    return new AuthenticationFailedException();
                                });

        if (!passwordVerifier.matches(command.password(), user.passwordHash())) {
            LOG.info("認証失敗 reason=bad-password userId={}", user.id().value());
            throw new AuthenticationFailedException();
        }

        List<TenantMembership> memberships = membershipRepository.findByUserId(user.id());
        List<TenantId> tenantIds = memberships.stream().map(TenantMembership::tenantId).toList();

        String sessionToken =
                tokenIssuer.issueSessionToken(user.id(), tenantIds, SESSION_TOKEN_TTL);
        LOG.info("認証成功 userId={} tenantsCount={}", user.id().value(), memberships.size());
        return new Result(sessionToken, SESSION_TOKEN_TTL.toSeconds(), memberships);
    }

    private static UserEmail parseEmail(String raw) {
        try {
            return new UserEmail(raw);
        } catch (IllegalArgumentException e) {
            // 形式違反は認証失敗扱いに統一(列挙攻撃対策)
            throw new AuthenticationFailedException();
        }
    }

    /** ログ用に email を簡易マスク。 */
    private static String maskedEmail(String email) {
        if (email == null) return "<null>";
        int at = email.indexOf('@');
        if (at <= 1) return "***";
        return email.charAt(0) + "***" + email.substring(at);
    }
}
