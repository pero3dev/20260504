package com.example.inventory.identity.adapter.out.security;

import java.util.Arrays;
import java.util.List;

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
 *
 * <p>multi-client 対応(A5 follow-up²⁹): {@code platform.identity.federation.audience} は CSV を受け 入れる。
 * 例 "{@code abc123,def456,ghi789}"。 各 web app が独自の Cognito App Client で発行された access token を 同一 IB
 * で受けるとき、 audience claim がいずれか 1 つに一致すれば成功、 いずれにも一致しないなら reject。 単一値("{@code abc123}")も 後方互換で 1 要素
 * list として扱う。 空文字 / 空白だけ / blank なら audience 検証スキップ。
 */
@Component
public class NimbusIdpTokenVerifier implements IdpTokenVerifier {

    private final String issuerUri;
    private final String jwksUri;
    private final String expectedSubjectClaim;
    private final String expectedAudienceClaim;
    private final List<String> expectedAudienceValues;
    private final String groupsClaim;

    private volatile JwtDecoder cachedDecoder;

    public NimbusIdpTokenVerifier(
            @Value("${platform.identity.federation.issuer-uri:}") String issuerUri,
            @Value("${platform.identity.federation.jwks-uri:}") String jwksUri,
            @Value("${platform.identity.federation.subject-claim:email}")
                    String expectedSubjectClaim,
            @Value("${platform.identity.federation.audience-claim:client_id}")
                    String expectedAudienceClaim,
            @Value("${platform.identity.federation.audience:}") String expectedAudienceCsv,
            @Value("${platform.identity.federation.groups-claim:cognito:groups}")
                    String groupsClaim) {
        this.issuerUri = issuerUri;
        this.jwksUri = jwksUri;
        this.expectedSubjectClaim = expectedSubjectClaim;
        this.expectedAudienceClaim = expectedAudienceClaim;
        this.expectedAudienceValues = parseCsv(expectedAudienceCsv);
        this.groupsClaim = groupsClaim;
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
        return verifyDecoded(jwt);
    }

    /**
     * decode 済 Jwt から subject / audience / groups を取り出す本体。 audience 検証ロジックを単体テストするため
     * package-private に抽出してある(production 経路では {@link #verify(String)} 経由で呼ばれる)。
     */
    Subject verifyDecoded(Jwt jwt) {
        String subjectValue = stringClaim(jwt, expectedSubjectClaim);
        if (!StringUtils.hasText(subjectValue)) {
            throw new InvalidIdpTokenException("subject claim '" + expectedSubjectClaim + "' が空");
        }

        if (!expectedAudienceValues.isEmpty()) {
            String aud = stringClaim(jwt, expectedAudienceClaim);
            if (aud == null || !expectedAudienceValues.contains(aud)) {
                throw new InvalidIdpTokenException(
                        "audience claim '"
                                + expectedAudienceClaim
                                + "' が許可リストに含まれない(Cognito client_id 不一致の可能性)");
            }
        }
        List<String> groups = extractGroups(jwt);
        return new Subject(
                subjectValue,
                jwt.getIssuer() != null ? jwt.getIssuer().toString() : issuerUri,
                groups);
    }

    /**
     * groups claim を {@code List<String>} で取り出す。 Cognito は {@code cognito:groups: ["g1","g2"]} を吐く。
     * claim が無い / 配列でないなら 空リスト。
     */
    private List<String> extractGroups(Jwt jwt) {
        Object raw = jwt.getClaims().get(groupsClaim);
        if (raw instanceof List<?> list) {
            return list.stream().filter(o -> o != null).map(Object::toString).toList();
        }
        return List.of();
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

    private static List<String> parseCsv(String csv) {
        if (!StringUtils.hasText(csv)) return List.of();
        return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    private static String stringClaim(Jwt jwt, String name) {
        Object value = jwt.getClaims().get(name);
        return value == null ? null : value.toString();
    }
}
