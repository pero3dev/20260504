package com.example.inventory.manufacturing.domain.model;

import java.util.List;

/**
 * BOM(Bill of Materials)。製品 SKU 1 単位を作るのに必要な部品の構成。
 *
 * <p>マスタ参照用の不変 Value Object。本サービス内では Read のみで CUD はしない(MVP)。 構成は (productSkuCode, componentSkuCode)
 * で一意な行として {@code bom_component} に保持される。
 *
 * <p>WorkOrder.place 時にスナップショットされて指図側に焼き付くので、 BOM 改訂後でも既存指図の部品構成は当時のまま保たれる(将来の BOM 改訂対応の前提)。
 *
 * <p>{@code components} は不変リストで保持(コンストラクタで {@link List#copyOf} 済み)。
 */
public record Bom(String productSkuCode, List<BomComponent> components) {

    public Bom {
        if (productSkuCode == null || productSkuCode.isBlank())
            throw new IllegalArgumentException("productSkuCode は必須");
        if (components == null || components.isEmpty())
            throw new IllegalArgumentException("BOM は 1 つ以上の構成要素が必要");
        components = List.copyOf(components);
    }
}
