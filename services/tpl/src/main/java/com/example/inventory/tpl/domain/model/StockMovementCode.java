package com.example.inventory.tpl.domain.model;

import java.util.regex.Pattern;

/**
 * 入出庫コード(自然キー)。テナント内で一意。 ASN(Advanced Shipping Notice)番号や出荷指示書番号等、外部連携で参照される安定値。
 *
 * <p>形式は大文字英数 + ハイフン、3〜64 文字。
 */
public record StockMovementCode(String value) {

    private static final Pattern VALID = Pattern.compile("^[A-Z0-9][A-Z0-9-]{2,63}$");

    public StockMovementCode {
        if (value == null || !VALID.matcher(value).matches()) {
            throw new IllegalArgumentException("不正な StockMovementCode: " + value);
        }
    }
}
