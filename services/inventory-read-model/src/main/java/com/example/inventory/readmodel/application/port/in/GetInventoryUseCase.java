package com.example.inventory.readmodel.application.port.in;

import com.example.inventory.readmodel.domain.model.InventoryProjection;

/** 入力ポート: 在庫投影を取得する。 */
public interface GetInventoryUseCase {

    /** 見つからない場合は {@link InventoryNotFoundException} を投げる。 */
    InventoryProjection get(long inventoryId);
}
