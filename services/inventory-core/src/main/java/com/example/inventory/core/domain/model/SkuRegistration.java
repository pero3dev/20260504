package com.example.inventory.core.domain.model;

/**
 * Master Data から受信した SKU 投影レコード。 Inventory Core はマスタの権威を持たないため、本オブジェクトはドメインの完全な集約ではなく、 引当時に「SKU
 * が登録済みか」を判定するための投影専用 VO。
 *
 * <p>{@code version} は Master Data 側の集約バージョン(= 受信イベントの {@code versionAfter})。 同一 SKU について古い version
 * を後受信した場合の判定に用いる。
 */
public record SkuRegistration(
        SkuId code, long aggregateId, String name, String unitOfMeasure, long version) {

    public SkuRegistration {
        if (code == null) {
            throw new IllegalArgumentException("code は必須");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name は必須");
        }
        if (version < 1) {
            throw new IllegalArgumentException("version は 1 以上");
        }
    }
}
