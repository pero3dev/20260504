package com.example.inventory.core.application.usecase;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.inventory.commons.audit.Auditable;
import com.example.inventory.core.application.port.in.ReceiveInventoryCommand;
import com.example.inventory.core.application.port.in.ReceiveInventoryUseCase;
import com.example.inventory.core.application.port.in.UnknownSkuException;
import com.example.inventory.core.application.port.out.InventoryRepository;
import com.example.inventory.core.application.port.out.SkuRegistryPort;
import com.example.inventory.core.domain.model.Inventory;
import com.example.inventory.core.domain.model.InventoryId;
import com.example.inventory.core.domain.model.Quantity;

/**
 * 入荷ユースケース。集約をロード → receive → 永続化 の順に処理する。
 *
 * <p>{@link com.example.inventory.commons.audit.Auditable} で audit.log.v1 への記録、 {@link
 * InventoryRepository#save(Inventory)} 経由で outbox に inventory.movement.v1 を書く (Reserve と同じ
 * Transactional Outbox 整合性、ADR-0009)。
 */
@Service
public class ReceiveInventoryService implements ReceiveInventoryUseCase {

    private final InventoryRepository repository;
    private final SkuRegistryPort skuRegistry;

    public ReceiveInventoryService(InventoryRepository repository, SkuRegistryPort skuRegistry) {
        this.repository = repository;
        this.skuRegistry = skuRegistry;
    }

    @Override
    @Transactional
    @Auditable(
            action = "INVENTORY_RECEIVE",
            targetType = "Inventory",
            targetIdExpression = "#command.inventoryId")
    public void receive(ReceiveInventoryCommand command) {
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

        inventory.receive(new Quantity(command.quantity()));
        repository.save(inventory);
    }
}
