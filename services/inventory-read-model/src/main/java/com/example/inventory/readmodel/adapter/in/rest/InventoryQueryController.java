package com.example.inventory.readmodel.adapter.in.rest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.example.inventory.readmodel.adapter.in.rest.api.InventoryApi;
import com.example.inventory.readmodel.adapter.in.rest.api.model.Inventory;
import com.example.inventory.readmodel.application.port.in.GetInventoryUseCase;
import com.example.inventory.readmodel.domain.model.InventoryProjection;

/**
 * REST 入力アダプタ。OpenAPI 仕様(docs/openapi/inventory-core.yaml)から生成された {@link InventoryApi}
 * を実装する(ADR-0006)。
 *
 * <p>本コントローラは Read Model の責務に絞った {@code GET /v1/inventories/{id}} のみ提供。 引当(POST .../reservations)は
 * Inventory Core 側の責務。
 */
@RestController
public class InventoryQueryController implements InventoryApi {

    private final GetInventoryUseCase useCase;

    public InventoryQueryController(GetInventoryUseCase useCase) {
        this.useCase = useCase;
    }

    @Override
    public ResponseEntity<Inventory> getInventory(Long inventoryId) {
        InventoryProjection projection = useCase.get(inventoryId);
        Inventory dto = new Inventory();
        dto.setId(projection.id());
        dto.setSkuId(projection.skuId());
        dto.setLocationId(projection.locationId());
        dto.setAvailable(projection.available());
        dto.setReserved(projection.reserved());
        dto.setVersion(projection.version());
        return ResponseEntity.ok(dto);
    }
}
