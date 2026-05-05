package com.example.inventory.core.e2e;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * テスト用ダミー JwtDecoder。Spring Security の MockMvc {@code jwt()} ポストプロセッサが SecurityContext に
 * Authentication を直接入れる仕組みのため、実際の JwtDecoder は呼ばれない。
 *
 * <p>autoconfig が外部の OIDC discovery を起動時にフェッチしようとしないよう {@code
 * spring.security.oauth2.resourceserver.jwt.issuer-uri=} (空) を設定し、 本クラスを明示的に
 * {@code @SpringBootTest(classes={..., TestJwtDecoderConfig.class})} で読み込む。
 *
 * <p>ネストした @TestConfiguration は @SpringBootTest(classes=...) 明示時には自動検出されないため、 共有のトップレベル class
 * として切り出している。
 */
@TestConfiguration
public class TestJwtDecoderConfig {

    @Bean
    JwtDecoder jwtDecoder() {
        return token -> {
            throw new UnsupportedOperationException("テストでは jwt() ポストプロセッサを使うこと");
        };
    }
}
