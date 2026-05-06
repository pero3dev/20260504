package com.example.inventory.wholesale.domain.model;

import java.util.regex.Pattern;

/**
 * 受注コード(自然キー)。テナント内で一意。
 *
 * <p>形式は大文字英数 + ハイフン、3〜64 文字。EDI / 取引先 ERP / 請求書で参照される安定値。
 */
public record OrderCode(String value) {

    private static final Pattern VALID = Pattern.compile("^[A-Z0-9][A-Z0-9-]{2,63}$");

    public OrderCode {
        if (value == null || !VALID.matcher(value).matches()) {
            throw new IllegalArgumentException("不正な OrderCode: " + value);
        }
    }
}
