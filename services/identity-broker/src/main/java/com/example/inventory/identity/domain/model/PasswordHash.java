package com.example.inventory.identity.domain.model;

/** BCrypt ハッシュを格納する値オブジェクト。生パスワードはこの型に入らない。 */
public record PasswordHash(String value) {

    public PasswordHash {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("PasswordHash を空にすることはできません");
        }
    }
}
