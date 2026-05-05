package com.example.inventory.core.application.port.out;

import java.util.Optional;

import com.example.inventory.commons.persistence.AggregateRepository;
import com.example.inventory.core.domain.model.Inventory;
import com.example.inventory.core.domain.model.InventoryId;
import com.example.inventory.core.domain.model.LocationId;
import com.example.inventory.core.domain.model.SkuId;

/** Inventory 集約用の出力ポート。実装は {@code adapter.out.persistence} で MyBatis ベースに行う(ADR-0009)。 */
public interface InventoryRepository extends AggregateRepository<Inventory, InventoryId> {

    @Override
    Optional<Inventory> findById(InventoryId id);

    /**
     * (SKU, Location) で在庫レコードを 1 件だけ解決する。Retail/EC 等の業態系から受信した注文で、 在庫レコードへ自然キーから引き当てるために利用する (Saga
     * 連結)。
     */
    Optional<Inventory> findBySkuAndLocation(SkuId skuId, LocationId locationId);

    @Override
    Inventory save(Inventory aggregate);

    @Override
    void delete(Inventory aggregate);
}
