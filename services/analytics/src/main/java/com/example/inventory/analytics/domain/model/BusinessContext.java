package com.example.inventory.analytics.domain.model;

/**
 * 業態(bounded context)識別子。ADR-0002 の境界と一致。
 *
 * <p>Analytics は業態をまたぐ集計を持つので、明示的な enum で型安全に扱う。
 */
public enum BusinessContext {
    RETAIL,
    WHOLESALE,
    MANUFACTURING,
    TPL;

    public String dbValue() {
        return name().toLowerCase();
    }

    public static BusinessContext fromDbValue(String value) {
        return BusinessContext.valueOf(value.toUpperCase());
    }
}
