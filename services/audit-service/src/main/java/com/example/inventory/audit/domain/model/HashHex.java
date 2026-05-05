package com.example.inventory.audit.domain.model;

/**
 * SHA-256 を16進文字列で表した値オブジェクト。長さ 64 固定、小文字のみ。
 *
 * <p>{@link #INITIAL} はチェーンの初期値(全ゼロ)。テナントの最初のレコードはこれを {@code prev_hash} として使う。
 */
public record HashHex(String value) {

    public static final HashHex INITIAL = new HashHex("0".repeat(64));

    public HashHex {
        if (value == null || value.length() != 64 || !value.chars().allMatch(HashHex::isLowerHex)) {
            throw new IllegalArgumentException("不正な SHA-256 hex: " + value);
        }
    }

    private static boolean isLowerHex(int c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f');
    }
}
