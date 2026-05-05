package com.example.inventory.identity.domain.model;

import java.util.regex.Pattern;

/** メールアドレス。簡易バリデーション付き。 */
public record UserEmail(String value) {

    private static final Pattern PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    public UserEmail {
        if (value == null || value.length() > 254 || !PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("不正なメールアドレス: " + value);
        }
    }
}
