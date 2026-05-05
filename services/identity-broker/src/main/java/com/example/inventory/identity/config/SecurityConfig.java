package com.example.inventory.identity.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Identity Broker の Security 設定。
 *
 * <p>他のサービスと違い、このサービスは「JWT を発行する側」なので JWT リソースサーバ設定を 適用しない(自分で発行したトークンの検証は不要)。 認証エンドポイントは
 * permitAll、それ以外も permitAll(MVP)。
 *
 * <p>将来は管理 API(ユーザー登録、ロール変更等)を追加する際、それらは認証必須にする。 その時に commons-security の PlatformSecurity
 * を使う設計に切り替える。
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http.csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(reg -> reg.anyRequest().permitAll())
                .build();
    }
}
