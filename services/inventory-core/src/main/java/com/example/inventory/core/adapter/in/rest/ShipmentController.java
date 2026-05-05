package com.example.inventory.core.adapter.in.rest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.example.inventory.core.adapter.in.rest.api.ShipmentsApi;
import com.example.inventory.core.adapter.in.rest.api.model.MovementRequest;
import com.example.inventory.core.application.port.in.ShipInventoryCommand;
import com.example.inventory.core.application.port.in.ShipInventoryUseCase;

/** 出荷 REST 入力アダプタ。OpenAPI 生成 {@link ShipmentsApi} を実装。 */
@RestController
public class ShipmentController implements ShipmentsApi {

    private final ShipInventoryUseCase useCase;

    public ShipmentController(ShipInventoryUseCase useCase) {
        this.useCase = useCase;
    }

    @Override
    public ResponseEntity<Void> shipInventory(Long inventoryId, MovementRequest request) {
        useCase.ship(new ShipInventoryCommand(inventoryId, request.getQuantity()));
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }
}
