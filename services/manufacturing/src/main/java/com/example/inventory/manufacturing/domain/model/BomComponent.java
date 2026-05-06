package com.example.inventory.manufacturing.domain.model;

/**
 * BOM 構成要素。製品 1 単位を作るのに必要な部品 SKU と数量を表す Value Object。
 *
 * <p>{@link Bom} と {@link WorkOrder} の双方が同じ形のコンポーネントを保持する。 WorkOrder は place 時に Bom
 * の構成要素をスナップショットして焼き付けるので、 BOM 改訂後でも過去の指図は当時の構成を保つ。
 */
public record BomComponent(String componentSkuCode, int quantityPerUnit) {

    public BomComponent {
        if (componentSkuCode == null || componentSkuCode.isBlank())
            throw new IllegalArgumentException("componentSkuCode は必須");
        if (quantityPerUnit <= 0) throw new IllegalArgumentException("quantityPerUnit は正の値");
    }
}
