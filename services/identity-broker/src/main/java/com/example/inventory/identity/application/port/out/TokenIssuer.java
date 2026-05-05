package com.example.inventory.identity.application.port.out;

import java.time.Duration;
import java.util.List;

import com.example.inventory.commons.tenant.TenantId;
import com.example.inventory.identity.domain.model.TenantMembership;
import com.example.inventory.identity.domain.model.UserId;

/**
 * 各種トークンを署名・発行する出力ポート。
 *
 * <p>MVP は次の2種類:
 *
 * <ul>
 *   <li>{@link #issueSessionToken(UserId, List, Duration)} — テナント未選択の中間トークン。 {@code
 *       accessibleTenants} クレームを持つ。下流業務 API では使用不可。
 *   <li>{@link #issueAccessToken(UserId, TenantMembership, Duration)} — テナントスコープの 業務 API
 *       用トークン。{@code tenant_id, roles, scopes} を含む。
 * </ul>
 *
 * <p>署名は RSA(adapter/out/security の Nimbus ベース実装)。下流サービスは 同サービスの {@code /.well-known/jwks.json}
 * で検証する。
 */
public interface TokenIssuer {

    String issueSessionToken(UserId userId, List<TenantId> accessibleTenants, Duration ttl);

    String issueAccessToken(UserId userId, TenantMembership membership, Duration ttl);

    /** セッショントークンを検証し、含まれる userId を返す。失敗時は IllegalArgumentException。 */
    UserId verifySessionToken(String token);
}
