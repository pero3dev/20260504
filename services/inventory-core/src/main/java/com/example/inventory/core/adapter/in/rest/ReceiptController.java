package com.example.inventory.core.adapter.in.rest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.example.inventory.core.adapter.in.rest.api.ReceiptsApi;
import com.example.inventory.core.adapter.in.rest.api.model.MovementRequest;
import com.example.inventory.core.application.port.in.ReceiveInventoryCommand;
import com.example.inventory.core.application.port.in.ReceiveInventoryUseCase;

/** 入荷 REST 入力アダプタ。OpenAPI 生成 {@link ReceiptsApi} を実装。 */
@RestController
public class ReceiptController implements ReceiptsApi {

    private final ReceiveInventoryUseCase useCase;

    public ReceiptController(ReceiveInventoryUseCase useCase) {
        this.useCase = useCase;
    }

    @Override
    public ResponseEntity<Void> receiveInventory(Long inventoryId, MovementRequest request) {
        useCase.receive(new ReceiveInventoryCommand(inventoryId, request.getQuantity()));
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }
}
