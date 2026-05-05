package com.example.inventory.retail.domain.model;

import java.math.BigDecimal;

/**
 * 注文明細(Order 集約の Value Object)。
 *
 * <p>{@code lineNo} は集約内連番(1 始まり)。Order が同一 SKU の複数行を持つことを許可するため自然キー ではなく行番号で識別する。
 */
public record OrderItem(int lineNo, String skuCode, int quantity, BigDecimal unitPrice) {

    public OrderItem {
        if (lineNo < 1) throw new IllegalArgumentException("lineNo は 1 以上");
        if (skuCode == null || skuCode.isBlank()) throw new IllegalArgumentException("skuCode は必須");
        if (quantity <= 0) throw new IllegalArgumentException("quantity は正の値");
        if (unitPrice == null || unitPrice.signum() < 0)
            throw new IllegalArgumentException("unitPrice は非負必須");
    }

    public BigDecimal lineTotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
