package com.example.inventory.core.application.usecase;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.inventory.commons.audit.Auditable;
import com.example.inventory.commons.persistence.SnowflakeIdGenerator;
import com.example.inventory.core.application.port.in.ApplyStockMovementUseCase;
import com.example.inventory.core.application.port.in.InventoryNotFoundForOrderException;
import com.example.inventory.core.application.port.out.InventoryRepository;
import com.example.inventory.core.domain.model.Inventory;
import com.example.inventory.core.domain.model.LocationId;
import com.example.inventory.core.domain.model.Quantity;
import com.example.inventory.core.domain.model.ReservationId;
import com.example.inventory.core.domain.model.SkuId;

@Service
public class ApplyStockMovementService implements ApplyStockMovementUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(ApplyStockMovementService.class);

    private final InventoryRepository repository;
    private final SnowflakeIdGenerator idGenerator;

    public ApplyStockMovementService(
            InventoryRepository repository, SnowflakeIdGenerator idGenerator) {
        this.repository = repository;
        this.idGenerator = idGenerator;
    }

    @Override
    @Transactional
    @Auditable(
            action = "STOCK_MOVEMENT_APPLY",
            targetType = "Inventory",
            targetIdExpression = "#command.movementCode")
    public void apply(Command command) {
        Inventory inventory =
                repository
                        .findBySkuAndLocation(
                                new SkuId(command.skuCode()), new LocationId(command.locationId()))
                        .orElseThrow(
                                () ->
                                        new InventoryNotFoundForOrderException(
                                                command.skuCode(), command.locationId()));

        Quantity qty = new Quantity(command.quantity());
        switch (command.movementType()) {
            case "INBOUND":
                inventory.receive(qty);
                break;
            case "OUTBOUND":
                // 3PL の OUTBOUND は reserve を経ずに直接 available から減らすパターンが多いが、
                // MVP では既存の reserve+ship を即時実行で重ねて表現する。在庫不足は通常の
                // InsufficientStockException で 409 相当の挙動になる(失敗は DLQ)。
                ReservationId rid = new ReservationId(idGenerator.nextId());
                inventory.reserve(rid, qty);
                inventory.ship(qty);
                break;
            case "ADJUSTMENT":
                LOG.warn("ADJUSTMENT は MVP 未対応のためスキップ movementCode={}", command.movementCode());
                return;
            default:
                LOG.warn(
                        "未知の movementType={} movementCode={} をスキップ",
                        command.movementType(),
                        command.movementCode());
                return;
        }
        repository.save(inventory);
        LOG.info(
                "StockMovement {} を在庫に反映({} {} ×{})",
                command.movementCode(),
                command.movementType(),
                command.skuCode(),
                command.quantity());
    }
}
