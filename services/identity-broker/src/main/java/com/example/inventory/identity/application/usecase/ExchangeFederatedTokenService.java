package com.example.inventory.identity.application.usecase;

import java.time.Duration;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.inventory.commons.audit.Auditable;
import com.example.inventory.commons.tenant.TenantId;
import com.example.inventory.identity.application.port.in.AuthenticationFailedException;
import com.example.inventory.identity.application.port.in.ExchangeFederatedTokenUseCase;
import com.example.inventory.identity.application.port.out.IdpTokenVerifier;
import com.example.inventory.identity.application.port.out.IdpTokenVerifier.InvalidIdpTokenException;
import com.example.inventory.identity.application.port.out.TenantMembershipRepository;
import com.example.inventory.identity.application.port.out.TokenIssuer;
import com.example.inventory.identity.application.port.out.UserRepository;
import com.example.inventory.identity.domain.model.TenantMembership;
import com.example.inventory.identity.domain.model.User;
import com.example.inventory.identity.domain.model.UserEmail;

/**
 * 外部 IdP の access token を Identity Broker のセッショントークンへ交換する usecase(F2 phase C)。
 *
 * <p>Cognito の access token は `username` claim に email を載せる構成を想定。 取り出した email で内部 {@link User}
 * を検索し、 ヒットしない場合は {@link AuthenticationFailedException}。 列挙攻撃 対策のため token 不正 / subject 未 provision
 * / 内部 User 不在は全て同じ 401 として扱う。
 *
 * <p>{@code @Auditable} で成功 / 失敗を `audit.log.v1` へ記録(J-SOX 統制、 platform tenant)。
 */
@Service
public class ExchangeFederatedTokenService implements ExchangeFederatedTokenUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(ExchangeFederatedTokenService.class);
    private static final Duration SESSION_TOKEN_TTL = Duration.ofMinutes(5);

    private final IdpTokenVerifier idpTokenVerifier;
    private final UserRepository userRepository;
    private final TenantMembershipRepository membershipRepository;
    private final TokenIssuer tokenIssuer;

    public ExchangeFederatedTokenService(
            IdpTokenVerifier idpTokenVerifier,
            UserRepository userRepository,
            TenantMembershipRepository membershipRepository,
            TokenIssuer tokenIssuer) {
        this.idpTokenVerifier = idpTokenVerifier;
        this.userRepository = userRepository;
        this.membershipRepository = membershipRepository;
        this.tokenIssuer = tokenIssuer;
    }

    @Override
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
        User user =
                userRepository
                        .findByEmail(email)
                        .orElseThrow(
                                () -> {
                                    LOG.info(
                                            "federated 認証成功するも内部 User 不在 issuer={} subject(masked)={}",
                                            subject.issuer(),
                                            mask(subject.value()));
                                    return new AuthenticationFailedException();
                                });

        List<TenantMembership> memberships = membershipRepository.findByUserId(user.id());
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

    private static UserEmail parseEmailOrThrow(IdpTokenVerifier.Subject subject) {
        try {
            return new UserEmail(subject.value());
        } catch (IllegalArgumentException e) {
            // subject claim が email 形式でない構成は本 phase 未対応(将来 sub claim 直引き等)
            throw new AuthenticationFailedException();
        }
    }

    private static String mask(String value) {
        if (value == null || value.length() <= 2) return "***";
        int at = value.indexOf('@');
        if (at <= 1) return value.charAt(0) + "***";
        return value.charAt(0) + "***" + value.substring(at);
    }
}
