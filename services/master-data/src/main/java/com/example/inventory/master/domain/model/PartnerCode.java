package com.example.inventory.master.domain.model;

import java.util.regex.Pattern;

/**
 * Partner コード(自然キー)。テナント内で一意。大文字英数 + ハイフン、3〜64 文字。
 *
 * <p>取引先コードは EDI / 注文 / 出荷指示などの payload に含まれるため、永続的・安定的な値であること。
 */
public record PartnerCode(String value) {

    private static final Pattern VALID = Pattern.compile("^[A-Z0-9][A-Z0-9-]{2,63}$");

    public PartnerCode {
        if (value == null || !VALID.matcher(value).matches()) {
            throw new IllegalArgumentException("不正な Partner コード: " + value);
        }
    }
}
