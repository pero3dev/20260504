package com.example.inventory.identity.application.usecase;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.inventory.commons.audit.Auditable;
import com.example.inventory.commons.tenant.TenantId;
import com.example.inventory.identity.application.port.in.AuthenticationFailedException;
import com.example.inventory.identity.application.port.in.SelectTenantUseCase;
import com.example.inventory.identity.application.port.in.TenantAccessDeniedException;
import com.example.inventory.identity.application.port.out.TenantMembershipRepository;
import com.example.inventory.identity.application.port.out.TokenIssuer;
import com.example.inventory.identity.domain.model.TenantMembership;
import com.example.inventory.identity.domain.model.UserId;

/** セッショントークンとテナントIDから、テナントスコープのアクセストークンを発行する。 */
@Service
public class SelectTenantService implements SelectTenantUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(SelectTenantService.class);
    private static final Duration ACCESS_TOKEN_TTL = Duration.ofMinutes(15);

    private final TenantMembershipRepository membershipRepository;
    private final TokenIssuer tokenIssuer;

    public SelectTenantService(
            TenantMembershipRepository membershipRepository, TokenIssuer tokenIssuer) {
        this.membershipRepository = membershipRepository;
        this.tokenIssuer = tokenIssuer;
    }

    @Override
    @Auditable(
            action = "USER_SELECT_TENANT",
            targetType = "Tenant",
            targetIdExpression = "#command.tenantId")
    public Result selectTenant(Command command) {
        UserId userId;
        try {
            userId = tokenIssuer.verifySessionToken(command.sessionToken());
        } catch (RuntimeException e) {
            LOG.info("テナント選択失敗 reason=invalid-session-token: {}", e.toString());
            throw new AuthenticationFailedException();
        }

        TenantId tenantId = parseTenantId(command.tenantId());
        TenantMembership membership =
                membershipRepository
                        .findByUserAndTenant(userId, tenantId)
                        .orElseThrow(
                                () -> {
                                    LOG.info(
                                            "テナント選択拒否 userId={} tenantId={}",
                                            userId.value(),
                                            tenantId.value());
                                    return new TenantAccessDeniedException(tenantId.value());
                                });

        String accessToken = tokenIssuer.issueAccessToken(userId, membership, ACCESS_TOKEN_TTL);
        LOG.info(
                "テナント選択成功 userId={} tenantId={} roles={}",
                userId.value(),
                tenantId.value(),
                membership.roleNames());
        return new Result(accessToken, ACCESS_TOKEN_TTL.toSeconds());
    }

    private static TenantId parseTenantId(String raw) {
        try {
            return new TenantId(raw);
        } catch (IllegalArgumentException e) {
            throw new TenantAccessDeniedException(raw);
        }
    }
}
