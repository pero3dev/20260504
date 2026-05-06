package com.example.inventory.manufacturing.domain.model;

import java.util.regex.Pattern;

/**
 * 製造指図コード(自然キー)。テナント内で一意。
 *
 * <p>形式は大文字英数 + ハイフン、3〜64 文字。MES / 取引先 / 帳票で参照される安定値。
 */
public record WorkOrderCode(String value) {

    private static final Pattern VALID = Pattern.compile("^[A-Z0-9][A-Z0-9-]{2,63}$");

    public WorkOrderCode {
        if (value == null || !VALID.matcher(value).matches()) {
            throw new IllegalArgumentException("不正な WorkOrderCode: " + value);
        }
    }
}
