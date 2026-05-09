package com.example.inventory.identity.adapter.out.security;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.example.inventory.commons.tenant.TenantId;
import com.example.inventory.identity.application.port.out.TokenIssuer;
import com.example.inventory.identity.domain.model.TenantMembership;
import com.example.inventory.identity.domain.model.UserId;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWKMatcher;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

/**
 * Nimbus JOSE + RSA で JWT を署名・検証する {@link TokenIssuer} 実装(ADR-0007)。
 *
 * <p>署名鍵は {@link JWKSource} から取得(JwtKeyConfig が起動時に決定)。 {@code platform.identity.issuer} がトークンの
 * {@code iss} クレームになる。
 */
@Component
public class NimbusJwtTokenIssuer implements TokenIssuer {

    private final JWKSource<SecurityContext> jwkSource;
    private final String issuer;

    public NimbusJwtTokenIssuer(
            JWKSource<SecurityContext> jwkSource,
            @Value("${platform.identity.issuer:https://idp.example.com/}") String issuer) {
        this.jwkSource = jwkSource;
        this.issuer = issuer;
    }

    @Override
    public String issueSessionToken(UserId userId, List<TenantId> accessibleTenants, Duration ttl) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("token_use", "session");
        claims.put("accessible_tenants", accessibleTenants.stream().map(TenantId::value).toList());
        return sign(String.valueOf(userId.value()), null, claims, ttl);
    }

    @Override
    public String issueAccessToken(UserId userId, TenantMembership membership, Duration ttl) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("token_use", "access");
        claims.put("tenant_id", membership.tenantId().value());
        claims.put("roles", membership.roleNames());
        Map<String, List<String>> scopes = new HashMap<>();
        scopes.put("locations", membership.locationScopes());
        scopes.put("partners", membership.partnerScopes());
        claims.put("scopes", scopes);
        // Phase 1 では mfa_strength=low 固定(ステップアップ MFA 後続実装で更新する)
        claims.put("mfa_strength", "low");
        // ADR-0022 phase 5a: tenant 単位の運用言語を web 側 i18n.changeLanguage 用に流す
        claims.put("locale", membership.tenantLocale());
        return sign(String.valueOf(userId.value()), membership.tenantId().value(), claims, ttl);
    }

    @Override
    public UserId verifySessionToken(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            RSAKey key =
                    (RSAKey)
                            jwkSource
                                    .get(
                                            new JWKSelector(
                                                    new JWKMatcher.Builder()
                                                            .keyID(jwt.getHeader().getKeyID())
                                                            .build()),
                                            null)
                                    .get(0);
            if (!jwt.verify(new RSASSAVerifier(key.toRSAPublicKey()))) {
                throw new IllegalArgumentException("署名検証失敗");
            }
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            if (claims.getExpirationTime() == null
                    || claims.getExpirationTime().before(new java.util.Date())) {
                throw new IllegalArgumentException("有効期限切れ");
            }
            if (!"session".equals(claims.getStringClaim("token_use"))) {
                throw new IllegalArgumentException("token_use がセッショントークンではない");
            }
            return new UserId(Long.parseLong(claims.getSubject()));
        } catch (Exception e) {
            throw new IllegalArgumentException("セッショントークンの検証に失敗: " + e.getMessage(), e);
        }
    }

    private String sign(
            String subject, String audience, Map<String, Object> extraClaims, Duration ttl) {
        try {
            RSAKey key = firstSigningKey();
            JWTClaimsSet.Builder b =
                    new JWTClaimsSet.Builder()
                            .issuer(issuer)
                            .subject(subject)
                            .issueTime(java.util.Date.from(Instant.now()))
                            .expirationTime(java.util.Date.from(Instant.now().plus(ttl)))
                            .jwtID(java.util.UUID.randomUUID().toString());
            if (audience != null) {
                b.audience(audience);
            }
            extraClaims.forEach(b::claim);

            SignedJWT jwt =
                    new SignedJWT(
                            new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(),
                            b.build());
            jwt.sign(new RSASSASigner(key.toPrivateKey()));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException("JWT 署名に失敗", e);
        }
    }

    private RSAKey firstSigningKey() throws JOSEException {
        List<com.nimbusds.jose.jwk.JWK> keys =
                jwkSource.get(
                        new JWKSelector(
                                new JWKMatcher.Builder()
                                        .keyType(com.nimbusds.jose.jwk.KeyType.RSA)
                                        .build()),
                        null);
        if (keys.isEmpty()) {
            throw new IllegalStateException("署名用 RSA 鍵が見つからない");
        }
        return (RSAKey) keys.get(0);
    }
}
