package com.example.inventory.master.domain.model;

import java.util.regex.Pattern;

/**
 * Location コード(自然キー)。テナント内で一意。大文字英数 + ハイフン、3〜64 文字。
 *
 * <p>サロゲート ID({@link LocationId})とは別に、業務上の識別子としてエンドユーザーが参照する。 拠点コードは在庫イベント / EDI / WMS 連携などの
 * payload に含まれるため、永続的・安定的な値であること。
 */
public record LocationCode(String value) {

    private static final Pattern VALID = Pattern.compile("^[A-Z0-9][A-Z0-9-]{2,63}$");

    public LocationCode {
        if (value == null || !VALID.matcher(value).matches()) {
            throw new IllegalArgumentException("不正な Location コード: " + value);
        }
    }
}
