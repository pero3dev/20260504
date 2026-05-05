package com.example.inventory.retail.domain.model;

import java.util.regex.Pattern;

/**
 * 注文コード(自然キー)。テナント内で一意。 顧客向け表示用ID(例: ORD-2026-0001)。
 *
 * <p>形式は大文字英数 + ハイフン、3〜64 文字。EDI / 通知メール / 払戻し等で参照される安定値。
 */
public record OrderCode(String value) {

    private static final Pattern VALID = Pattern.compile("^[A-Z0-9][A-Z0-9-]{2,63}$");

    public OrderCode {
        if (value == null || !VALID.matcher(value).matches()) {
            throw new IllegalArgumentException("不正な OrderCode: " + value);
        }
    }
}
