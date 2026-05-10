package com.example.inventory.commons.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * {@code @Bean SecurityFilterChain ...} メソッドが {@link
 * PlatformSecurity#applyDefaults(org.springframework.security.config.annotation.web.builders.HttpSecurity)}
 * を経由しないことを明示的に許す例外マーカ。
 *
 * <p>ArchUnit ルール {@code SecurityRules.securityFilterChainsUsePlatformDefaults} は、 全 service の
 * {@code @Configuration} 配下の {@code SecurityFilterChain} Bean が共通設定経路を踏むことを CI 強制する。 正当な例外(MVP
 * 公開エンドポイントの permitAll filter chain や、 loadtest profile 専用の認証バイパス chain など) は本マーカを {@code reason}
 * 必須で付与すれば ArchUnit を pass する。
 *
 * <p>例:
 *
 * <pre>{@code
 * @Bean
 * @Order(LOWEST_PRECEDENCE)
 * @SecurityFilterChainExempt(
 *     reason = "MVP 公開エンドポイント (login / JWKS) は permitAll、 Bearer 検証経路を持たない")
 * public SecurityFilterChain publicFilterChain(HttpSecurity http) throws Exception { ... }
 * }</pre>
 *
 * <p><b>運用ルール</b>: 新規追加時はコードレビューで reason の妥当性を確認。 安易に付けるとプラットフォーム共通の Bearer / Tenant /
 * RevocationCheck filter が通らないため、 該当 chain が叩く endpoint の路地名と判断根拠を残すこと。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SecurityFilterChainExempt {

    /**
     * なぜ {@link PlatformSecurity#applyDefaults} を経由しないか。 空文字列禁止(コンパイル時には強制できないので code review で担保)。
     */
    String reason();
}
