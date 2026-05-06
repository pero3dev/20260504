package com.example.inventory.wholesale.domain.model;

import java.math.BigDecimal;

/**
 * 取引先別契約価格(参照用 Value Object)。
 *
 * <p>Wholesale 業態の核心: 同じ SKU でも取引先によって単価が異なる。 受注確定時に (partnerCode, skuCode) で本テーブルを引き、
 * line.unitPrice のスナップショットとして使う。
 *
 * <p>本 VO は不変。価格変更があっても過去の {@link Order#items()}.unitPrice は受注時点を保持する。
 */
public record PartnerPrice(
        PartnerCode partnerCode, String skuCode, BigDecimal unitPrice, String currency) {

    public PartnerPrice {
        if (skuCode == null || skuCode.isBlank()) throw new IllegalArgumentException("skuCode は必須");
        if (unitPrice == null || unitPrice.signum() < 0)
            throw new IllegalArgumentException("unitPrice は非負必須");
        if (currency == null || currency.length() != 3)
            throw new IllegalArgumentException("currency は 3 文字 ISO 4217");
    }
}
