package com.example.inventory.identity.adapter.out.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.example.inventory.identity.application.port.out.IdpTokenVerifier;

/**
 * 外部 IdP(Cognito 等)の access token を Spring Security の {@link JwtDecoder}(内部 Nimbus) で検証する
 * adapter(F2 phase C)。
 *
 * <p>{@code platform.identity.federation.*} 設定が空の環境では、 全ての verify が {@link
 * InvalidIdpTokenException} を throw する(production で federation を有効化する場合に のみ Cognito issuer + client
 * id を構成する想定)。
 *
 * <p>Cognito access token は標準 OAuth2 access token に従い、 audience claim は通常 `client_id` (`aud`
 * ではない)に格納される。 そのため audience は手動 claim 比較で検査する。
 */
@Component
public class NimbusIdpTokenVerifier implements IdpTokenVerifier {

    private final String issuerUri;
    private final String jwksUri;
    private final String expectedSubjectClaim;
    private final String expectedAudienceClaim;
    private final String expectedAudienceValue;

    private volatile JwtDecoder cachedDecoder;

    public NimbusIdpTokenVerifier(
            @Value("${platform.identity.federation.issuer-uri:}") String issuerUri,
            @Value("${platform.identity.federation.jwks-uri:}") String jwksUri,
            @Value("${platform.identity.federation.subject-claim:email}")
                    String expectedSubjectClaim,
            @Value("${platform.identity.federation.audience-claim:client_id}")
                    String expectedAudienceClaim,
            @Value("${platform.identity.federation.audience:}") String expectedAudienceValue) {
        this.issuerUri = issuerUri;
        this.jwksUri = jwksUri;
        this.expectedSubjectClaim = expectedSubjectClaim;
        this.expectedAudienceClaim = expectedAudienceClaim;
        this.expectedAudienceValue = expectedAudienceValue;
    }

    @Override
    public Subject verify(String accessToken) {
        if (!isConfigured()) {
            throw new InvalidIdpTokenException(
                    "federation 未構成(platform.identity.federation.issuer-uri 等を設定してください)");
        }
        Jwt jwt;
        try {
            jwt = decoder().decode(accessToken);
        } catch (JwtException e) {
            throw new InvalidIdpTokenException("federated token の検証に失敗: " + e.getMessage(), e);
        }

        String subjectValue = stringClaim(jwt, expectedSubjectClaim);
        if (!StringUtils.hasText(subjectValue)) {
            throw new InvalidIdpTokenException("subject claim '" + expectedSubjectClaim + "' が空");
        }

        if (StringUtils.hasText(expectedAudienceValue)) {
            String aud = stringClaim(jwt, expectedAudienceClaim);
            if (!expectedAudienceValue.equals(aud)) {
                throw new InvalidIdpTokenException(
                        "audience claim '"
                                + expectedAudienceClaim
                                + "' が期待値と一致しない(Cognito client_id 不一致の可能性)");
            }
        }
        return new Subject(
                subjectValue, jwt.getIssuer() != null ? jwt.getIssuer().toString() : issuerUri);
    }

    private boolean isConfigured() {
        return StringUtils.hasText(issuerUri);
    }

    private JwtDecoder decoder() {
        JwtDecoder local = cachedDecoder;
        if (local != null) return local;
        synchronized (this) {
            if (cachedDecoder == null) {
                NimbusJwtDecoder built =
                        StringUtils.hasText(jwksUri)
                                ? NimbusJwtDecoder.withJwkSetUri(jwksUri).build()
                                : NimbusJwtDecoder.withIssuerLocation(issuerUri).build();
                // 既定 validator(timestamp)+ issuer 一致を強制
                built.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuerUri));
                cachedDecoder = built;
            }
            return cachedDecoder;
        }
    }

    private static String stringClaim(Jwt jwt, String name) {
        Object value = jwt.getClaims().get(name);
        return value == null ? null : value.toString();
    }
}
