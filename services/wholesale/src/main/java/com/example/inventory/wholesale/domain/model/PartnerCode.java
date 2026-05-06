package com.example.inventory.wholesale.domain.model;

import java.util.regex.Pattern;

/**
 * 取引先コード(自然キー)。master-data の Partner と同 ID 体系。
 *
 * <p>本サービスは master-data の権威を借りる側で、Partner マスタ実体は Master Data Service が持つ。 ここでは値の形式チェックのみ行う。
 */
public record PartnerCode(String value) {

    private static final Pattern VALID = Pattern.compile("^[A-Z0-9][A-Z0-9-]{2,63}$");

    public PartnerCode {
        if (value == null || !VALID.matcher(value).matches()) {
            throw new IllegalArgumentException("不正な PartnerCode: " + value);
        }
    }
}
