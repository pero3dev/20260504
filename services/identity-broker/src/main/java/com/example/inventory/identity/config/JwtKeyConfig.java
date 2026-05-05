package com.example.inventory.identity.config;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;

/**
 * JWT 署名用 RSA 鍵の Bean 配線。
 *
 * <p>MVP は **起動時にプロセスローカルで RSA 鍵ペアを生成** する。 これは開発・テスト用途のみ。本番では:
 *
 * <ul>
 *   <li>AWS KMS / Secrets Manager から PEM を読み込む実装に差し替える
 *   <li>鍵ローテーション(複数 keyID を JWKS に出す)を実装する
 * </ul>
 *
 * <p>本クラスは敢えて {@code @Profile("!production")} 等の制約は付けず、デフォルトで動かす。 本番設定に切り替える時は鍵ロード方式と合わせて Bean
 * を上書きする。
 */
@Configuration
public class JwtKeyConfig {

    private static final Logger LOG = LoggerFactory.getLogger(JwtKeyConfig.class);

    @Bean
    public JWKSource<SecurityContext> jwkSource() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();

        String keyId = "dev-" + UUID.randomUUID();
        RSAKey rsaKey = new RSAKey.Builder(publicKey).privateKey(privateKey).keyID(keyId).build();
        LOG.warn("⚠️ プロセス内で RSA 鍵を生成しました(MVP 用、本番では Secrets Manager 連携に差し替え): keyId={}", keyId);
        return new ImmutableJWKSet<>(new JWKSet(rsaKey));
    }
}
