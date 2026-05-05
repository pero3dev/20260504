package com.example.inventory.readmodel.application.port.out;

import java.util.Optional;

import com.example.inventory.readmodel.domain.model.InventoryProjection;

/**
 * Read Model の投影を保存・取得するための出力ポート。
 *
 * <p>実装は Redis(ElastiCache、ADR-0004)。テナント分離はキー名前空間で実現する。 呼び出しは {@code TenantContext} 配下で行うこと。
 */
public interface InventoryProjectionStore {

    Optional<InventoryProjection> findById(long inventoryId);

    /** 投影を上書き保存(冪等性チェックは呼出側の責務)。 */
    void save(InventoryProjection projection);
}
