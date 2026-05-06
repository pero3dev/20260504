package com.example.inventory.wholesale.domain.model;

import java.math.BigDecimal;

/**
 * 受注明細(Order 集約の Value Object)。
 *
 * <p>{@code unitPrice} は受注時の {@link PartnerPrice} スナップショット。価格変更があっても受注時点を保持する。
 *
 * <p>{@code locationId} は出荷元拠点。Inventory Core が (SKU, Location) で在庫レコードを 解決して引当てるために必要。
 */
public record OrderItem(
        int lineNo, String skuCode, String locationId, int quantity, BigDecimal unitPrice) {

    public OrderItem {
        if (lineNo < 1) throw new IllegalArgumentException("lineNo は 1 以上");
        if (skuCode == null || skuCode.isBlank()) throw new IllegalArgumentException("skuCode は必須");
        if (locationId == null || locationId.isBlank())
            throw new IllegalArgumentException("locationId は必須");
        if (quantity <= 0) throw new IllegalArgumentException("quantity は正の値");
        if (unitPrice == null || unitPrice.signum() < 0)
            throw new IllegalArgumentException("unitPrice は非負必須");
    }

    public BigDecimal lineTotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
