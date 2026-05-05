package com.example.inventory.readmodel.application.usecase;

import org.springframework.stereotype.Service;

import com.example.inventory.commons.audit.Auditable;
import com.example.inventory.readmodel.application.port.in.GetInventoryUseCase;
import com.example.inventory.readmodel.application.port.in.InventoryNotFoundException;
import com.example.inventory.readmodel.application.port.out.InventoryProjectionStore;
import com.example.inventory.readmodel.domain.model.InventoryProjection;

/**
 * 在庫投影を取得する。
 *
 * <p>{@code @Auditable(read=true)} で監査記録に残す — 在庫の参照は統制対象として 計上する(ADR-0008)。Read Model は DB
 * を持たないため、commons-event の {@code DirectKafkaDomainEventPublisher} 経由で {@code audit.log.v1} に直接発行する
 * (at-most-once)。Audit Service 側で「重要参照」フィルタ後に WORM に永続化する前提。
 */
@Service
public class GetInventoryService implements GetInventoryUseCase {

    private final InventoryProjectionStore store;

    public GetInventoryService(InventoryProjectionStore store) {
        this.store = store;
    }

    @Override
    @Auditable(
            action = "INVENTORY_QUERY",
            targetType = "Inventory",
            targetIdExpression = "#inventoryId",
            read = true)
    public InventoryProjection get(long inventoryId) {
        return store.findById(inventoryId)
                .orElseThrow(() -> new InventoryNotFoundException(inventoryId));
    }
}
