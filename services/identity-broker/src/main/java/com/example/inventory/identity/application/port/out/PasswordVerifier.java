package com.example.inventory.identity.application.port.out;

import com.example.inventory.identity.domain.model.PasswordHash;

/**
 * 生パスワードと {@link PasswordHash} の照合ポート。実装は BCrypt(adapter/out/security)。 ドメインに依存させず付け替え可能にしておく(将来
 * Argon2 等への移行余地)。
 */
public interface PasswordVerifier {

    boolean matches(String rawPassword, PasswordHash hash);
}
