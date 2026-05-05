package com.example.inventory.identity.adapter.in.rest;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.inventory.identity.adapter.in.rest.api.JwksApi;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;

/**
 * JWKS エンドポイント。下流サービス(inventory-core, inventory-read-model 等)が このエンドポイントから公開鍵を取得して、本サービスが発行した JWT
 * を検証する。
 *
 * <p>OpenAPI の {@link JwksApi} は戻り値が汎用 Object マップなので、ここでは {@code @GetMapping} を直接書いて {@link
 * JWKSet#toJSONObject()} を返す。
 */
@RestController
public class JwksController implements JwksApi {

    private final JWKSource<SecurityContext> jwkSource;

    public JwksController(JWKSource<SecurityContext> jwkSource) {
        this.jwkSource = jwkSource;
    }

    @Override
    @GetMapping("/.well-known/jwks.json")
    public ResponseEntity<Map<String, Object>> getJwks() {
        try {
            JWKSet set =
                    new JWKSet(
                            jwkSource.get(
                                    new com.nimbusds.jose.jwk.JWKSelector(
                                            new com.nimbusds.jose.jwk.JWKMatcher.Builder().build()),
                                    null));
            // toJSONObject() で公開鍵のみ(秘密鍵パートは含まれない)
            return ResponseEntity.ok(set.toJSONObject(true));
        } catch (com.nimbusds.jose.KeySourceException e) {
            throw new IllegalStateException("JWKS の取得に失敗", e);
        }
    }
}
