package com.example.inventory.core.domain.model;

/** SKU(商品)識別子。Master Data サービスが管理する自然キーをそのまま受け取る。 */
public record SkuId(String value) {

    public SkuId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("SkuId を空にすることはできません");
        }
    }
}
