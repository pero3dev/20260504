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
import com.example.inventory.identity.application.port.out.TenantRepository;
import com.example.inventory.identity.application.port.out.TokenIssuer;
import com.example.inventory.identity.domain.model.TenantMembership;
import com.example.inventory.identity.domain.model.TenantStatus;
import com.example.inventory.identity.domain.model.UserId;

/**
 * セッショントークンとテナントIDから、テナントスコープのアクセストークンを発行する。
 *
 * <p>tenant lifecycle 整合性:
 *
 * <ul>
 *   <li>membership が無い → {@link TenantAccessDeniedException}(列挙攻撃対策、 「存在するが 未所属」と「存在しない」を同じ扱いに)
 *   <li>membership は有るが {@link TenantStatus#DEACTIVATED} → {@link TenantAccessDeniedException}(過去
 *       user 在籍時の access token 残留を新規 issue で延長 させない、 tenant deactivation の意味的な幅を確保)
 * </ul>
 */
@Service
public class SelectTenantService implements SelectTenantUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(SelectTenantService.class);
    private static final Duration ACCESS_TOKEN_TTL = Duration.ofMinutes(15);

    private final TenantMembershipRepository membershipRepository;
    private final TenantRepository tenantRepository;
    private final TokenIssuer tokenIssuer;

    public SelectTenantService(
            TenantMembershipRepository membershipRepository,
            TenantRepository tenantRepository,
            TokenIssuer tokenIssuer) {
        this.membershipRepository = membershipRepository;
        this.tenantRepository = tenantRepository;
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
                                            "テナント選択拒否(membership 不在) userId={} tenantId={}",
                                            userId.value(),
                                            tenantId.value());
                                    return new TenantAccessDeniedException(tenantId.value());
                                });

        // tenant status が DEACTIVATED なら新規 access token は発行しない。 既発行 token は
        // TTL 切れまで有効(stateless JWT を per-tenant revocation するには別 mechanism が必要)。
        // membership が有るのに tenant row 不在は data inconsistency(運用イベントで対処、
        // ここでは認可拒否で安全側に倒す)。
        var tenant =
                tenantRepository
                        .findById(tenantId)
                        .orElseThrow(
                                () -> {
                                    LOG.warn(
                                            "テナント選択拒否(tenant row 不在 / membership 不整合) userId={} tenantId={}",
                                            userId.value(),
                                            tenantId.value());
                                    return new TenantAccessDeniedException(tenantId.value());
                                });
        if (tenant.status() == TenantStatus.DEACTIVATED) {
            LOG.info(
                    "テナント選択拒否(tenant DEACTIVATED) userId={} tenantId={}",
                    userId.value(),
                    tenantId.value());
            throw new TenantAccessDeniedException(tenantId.value());
        }

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
