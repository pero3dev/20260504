package com.example.inventory.core.application.usecase;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.inventory.commons.audit.Auditable;
import com.example.inventory.commons.persistence.SnowflakeIdGenerator;
import com.example.inventory.core.application.port.in.ConsumeWorkOrderComponentsUseCase;
import com.example.inventory.core.application.port.in.InventoryNotFoundForOrderException;
import com.example.inventory.core.application.port.out.InventoryRepository;
import com.example.inventory.core.domain.model.Inventory;
import com.example.inventory.core.domain.model.LocationId;
import com.example.inventory.core.domain.model.Quantity;
import com.example.inventory.core.domain.model.ReservationId;
import com.example.inventory.core.domain.model.SkuId;

/**
 * Manufacturing の WorkOrder release を受けて構成要素分の在庫を消費する。
 *
 * <p>1 構成要素 = reserve + ship を即時連続実行(3PL OUTBOUND と同じ手順)。 1 メッセージ = 1 トランザクションなので、複数構成要素の消費は
 * all-or-nothing。 在庫不足(InsufficientStockException)等が起きると Spring の @Transactional で 全部ロールバック → 補償発行は
 * Listener 側で行う。
 */
@Service
public class ConsumeWorkOrderComponentsService implements ConsumeWorkOrderComponentsUseCase {

    private static final Logger LOG =
            LoggerFactory.getLogger(ConsumeWorkOrderComponentsService.class);

    private final InventoryRepository inventoryRepository;
    private final SnowflakeIdGenerator idGenerator;

    public ConsumeWorkOrderComponentsService(
            InventoryRepository inventoryRepository, SnowflakeIdGenerator idGenerator) {
        this.inventoryRepository = inventoryRepository;
        this.idGenerator = idGenerator;
    }

    @Override
    @Transactional
    @Auditable(
            action = "WORK_ORDER_CONSUME",
            targetType = "WorkOrder",
            targetIdExpression = "#command.workOrderCode")
    public void consume(Command command) {
        LocationId location = new LocationId(command.locationId());
        for (Command.Line line : command.components()) {
            Inventory inventory =
                    inventoryRepository
                            .findBySkuAndLocation(new SkuId(line.componentSkuCode()), location)
                            .orElseThrow(
                                    () ->
                                            new InventoryNotFoundForOrderException(
                                                    line.componentSkuCode(), command.locationId()));

            Quantity qty = new Quantity(line.requiredQuantity());
            ReservationId reservationId = new ReservationId(idGenerator.nextId());
            inventory.reserve(reservationId, qty);
            inventory.ship(qty);
            inventoryRepository.save(inventory);
        }
        LOG.info(
                "WorkOrder {} の部品消費を完了({}構成要素 @ {})",
                command.workOrderCode(),
                command.components().size(),
                command.locationId());
    }
}
