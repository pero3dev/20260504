package com.example.inventory.identity.adapter.out.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import com.example.inventory.identity.application.port.out.IdpTokenVerifier.InvalidIdpTokenException;
import com.example.inventory.identity.application.port.out.IdpTokenVerifier.Subject;

/**
 * {@link NimbusIdpTokenVerifier} の post-decode 検証ロジック単体テスト(A5 follow-up²⁹)。
 *
 * <p>{@link NimbusIdpTokenVerifier#verify(String)} は内部で Nimbus が JWT 署名 / 期限 / issuer 検証する経路。
 * 本テストでは package-private な {@link NimbusIdpTokenVerifier#verifyDecoded(Jwt)} を直接呼び、 audience
 * 許可リスト方式と subject / groups の取り出しのみをカバーする。
 */
class NimbusIdpTokenVerifierTest {

    private static final String ISSUER = "https://cognito-idp.ap-northeast-1.amazonaws.com/pool-1";

    @Test
    void audience_未設定なら_audience_check_を_skip_する() {
        NimbusIdpTokenVerifier verifier = newVerifier("");

        Subject s = verifier.verifyDecoded(jwtWith("user@example.com", null, List.of()));

        assertThat(s.value()).isEqualTo("user@example.com");
    }

    @Test
    void audience_1件_一致なら_成功() {
        NimbusIdpTokenVerifier verifier = newVerifier("client-A");

        Subject s = verifier.verifyDecoded(jwtWith("user@example.com", "client-A", List.of()));

        assertThat(s.value()).isEqualTo("user@example.com");
    }

    @Test
    void audience_1件_不一致なら_reject() {
        NimbusIdpTokenVerifier verifier = newVerifier("client-A");

        assertThatThrownBy(
                        () ->
                                verifier.verifyDecoded(
                                        jwtWith("user@example.com", "client-X", List.of())))
                .isInstanceOf(InvalidIdpTokenException.class)
                .hasMessageContaining("許可リスト");
    }

    @Test
    void audience_CSV_複数_いずれか一致なら_成功() {
        NimbusIdpTokenVerifier verifier = newVerifier("client-A,client-B,client-C");

        // 最初の値
        assertThat(verifier.verifyDecoded(jwtWith("u1@example.com", "client-A", List.of())).value())
                .isEqualTo("u1@example.com");
        // 真ん中
        assertThat(verifier.verifyDecoded(jwtWith("u2@example.com", "client-B", List.of())).value())
                .isEqualTo("u2@example.com");
        // 末尾
        assertThat(verifier.verifyDecoded(jwtWith("u3@example.com", "client-C", List.of())).value())
                .isEqualTo("u3@example.com");
    }

    @Test
    void audience_CSV_複数_どれにも一致しないなら_reject() {
        NimbusIdpTokenVerifier verifier = newVerifier("client-A,client-B,client-C");

        assertThatThrownBy(
                        () ->
                                verifier.verifyDecoded(
                                        jwtWith("user@example.com", "client-X", List.of())))
                .isInstanceOf(InvalidIdpTokenException.class)
                .hasMessageContaining("許可リスト");
    }

    @Test
    void audience_CSV_前後_空白_と_空要素_は_除去する() {
        // " client-A , , client-B " → ["client-A","client-B"]
        NimbusIdpTokenVerifier verifier = newVerifier(" client-A , , client-B ");

        assertThat(verifier.verifyDecoded(jwtWith("u@example.com", "client-A", List.of())).value())
                .isEqualTo("u@example.com");
        assertThat(verifier.verifyDecoded(jwtWith("u@example.com", "client-B", List.of())).value())
                .isEqualTo("u@example.com");
        assertThatThrownBy(() -> verifier.verifyDecoded(jwtWith("u@example.com", "", List.of())))
                .isInstanceOf(InvalidIdpTokenException.class);
    }

    @Test
    void audience_設定済_だが_token_に_audience_claim_欠落なら_reject() {
        NimbusIdpTokenVerifier verifier = newVerifier("client-A");

        assertThatThrownBy(() -> verifier.verifyDecoded(jwtWith("u@example.com", null, List.of())))
                .isInstanceOf(InvalidIdpTokenException.class);
    }

    @Test
    void subject_claim_欠落なら_reject() {
        NimbusIdpTokenVerifier verifier = newVerifier("client-A");

        assertThatThrownBy(() -> verifier.verifyDecoded(jwtWith(null, "client-A", List.of())))
                .isInstanceOf(InvalidIdpTokenException.class)
                .hasMessageContaining("subject claim");
    }

    @Test
    void groups_claim_は_リスト型_なら_String_化して_返す() {
        NimbusIdpTokenVerifier verifier = newVerifier("");

        Subject s =
                verifier.verifyDecoded(
                        jwtWith("u@example.com", null, List.of("admins", "auditors")));

        assertThat(s.groups()).containsExactly("admins", "auditors");
    }

    private static NimbusIdpTokenVerifier newVerifier(String audienceCsv) {
        return new NimbusIdpTokenVerifier(
                ISSUER, "", "email", "client_id", audienceCsv, "cognito:groups");
    }

    private static Jwt jwtWith(String email, String clientId, List<String> groups) {
        Jwt.Builder b =
                Jwt.withTokenValue("dummy")
                        .header("alg", "RS256")
                        .issuer(ISSUER)
                        .issuedAt(Instant.parse("2026-05-10T00:00:00Z"))
                        .expiresAt(Instant.parse("2026-05-10T01:00:00Z"));
        if (email != null) b.claim("email", email);
        if (clientId != null) b.claim("client_id", clientId);
        if (!groups.isEmpty()) b.claim("cognito:groups", groups);
        return b.build();
    }
}
