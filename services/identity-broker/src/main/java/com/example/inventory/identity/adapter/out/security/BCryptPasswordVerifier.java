package com.example.inventory.identity.adapter.out.security;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import com.example.inventory.identity.application.port.out.PasswordVerifier;
import com.example.inventory.identity.domain.model.PasswordHash;

/**
 * Spring Security の {@link BCryptPasswordEncoder} を使った {@link PasswordVerifier} 実装。 cost factor は標準
 * 10。負荷試験で必要なら 12 まで上げる(本番環境ベンチで確定)。
 */
@Component
public class BCryptPasswordVerifier implements PasswordVerifier {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10);

    @Override
    public boolean matches(String rawPassword, PasswordHash hash) {
        if (rawPassword == null || hash == null) {
            return false;
        }
        return encoder.matches(rawPassword, hash.value());
    }
}
