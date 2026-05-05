package com.example.inventory.identity;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Identity Broker — Cognito 前段に置く認証ハブ(ADR-0007)。
 *
 * <p>本サービスの責務:
 *
 * <ul>
 *   <li>クレデンシャル/SAML フェデレーションでユーザー認証(MVPはローカル DB のみ)
 *   <li>テナントメンバーシップ解決
 *   <li>テナントスコープ JWT の署名・発行(15分)
 *   <li>JWKS の公開(下流サービスが検証用に取得)
 *   <li>(将来) ステップアップ MFA、リフレッシュトークン
 * </ul>
 *
 * <p>マルチテナンシ方式は Pool(共通DB + tenant_id 列、ADR-0003)。
 */
@SpringBootApplication
@EnableScheduling
public class IdentityBrokerApplication {

    public static void main(String[] args) {
        SpringApplication.run(IdentityBrokerApplication.class, args);
    }
}
