package com.example.inventory.core.application.usecase;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.inventory.commons.audit.Auditable;
import com.example.inventory.core.application.port.in.ShipInventoryCommand;
import com.example.inventory.core.application.port.in.ShipInventoryUseCase;
import com.example.inventory.core.application.port.in.UnknownSkuException;
import com.example.inventory.core.application.port.out.InventoryRepository;
import com.example.inventory.core.application.port.out.SkuRegistryPort;
import com.example.inventory.core.domain.model.Inventory;
import com.example.inventory.core.domain.model.InventoryId;
import com.example.inventory.core.domain.model.Quantity;

/**
 * 出荷ユースケース。引当済み数量を消化する({@code reserved -= quantity})。 available には影響しない(引当時点で既に available
 * から差し引かれている)。
 */
@Service
public class ShipInventoryService implements ShipInventoryUseCase {

    private final InventoryRepository repository;
    private final SkuRegistryPort skuRegistry;

    public ShipInventoryService(InventoryRepository repository, SkuRegistryPort skuRegistry) {
        this.repository = repository;
        this.skuRegistry = skuRegistry;
    }

    @Override
    @Transactional
    @Auditable(
            action = "INVENTORY_SHIP",
            targetType = "Inventory",
            targetIdExpression = "#command.inventoryId")
    public void ship(ShipInventoryCommand command) {
        Inventory inventory =
                repository
                        .findById(new InventoryId(command.inventoryId()))
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "在庫が見つかりません: " + command.inventoryId()));

        if (!skuRegistry.exists(inventory.skuId())) {
            throw new UnknownSkuException(inventory.skuId());
        }

        inventory.ship(new Quantity(command.quantity()));
        repository.save(inventory);
    }
}
