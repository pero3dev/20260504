package com.example.inventory.core.application.usecase;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.inventory.commons.audit.Auditable;
import com.example.inventory.commons.persistence.SnowflakeIdGenerator;
import com.example.inventory.core.application.port.in.InventoryNotFoundForOrderException;
import com.example.inventory.core.application.port.in.ReserveOrderUseCase;
import com.example.inventory.core.application.port.out.InventoryRepository;
import com.example.inventory.core.domain.model.Inventory;
import com.example.inventory.core.domain.model.LocationId;
import com.example.inventory.core.domain.model.Quantity;
import com.example.inventory.core.domain.model.ReservationId;
import com.example.inventory.core.domain.model.SkuId;

/**
 * Retail/EC 等の {@code retail.order.placed.v1} を消費して、注文の各明細を在庫引当する。
 *
 * <p>1 メッセージ = 1 トランザクション。途中で {@code InsufficientStockException} 等が起きると、 Spring の @Transactional
 * によりロールバックされ、その注文の引当は取り消される。Compensation の 補償イベント発行は Phase 2(本クラスではいまだ未実装、失敗は単に DLQ で観測される)。
 */
@Service
public class ReserveOrderService implements ReserveOrderUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(ReserveOrderService.class);

    private final InventoryRepository inventoryRepository;
    private final SnowflakeIdGenerator idGenerator;

    public ReserveOrderService(
            InventoryRepository inventoryRepository, SnowflakeIdGenerator idGenerator) {
        this.inventoryRepository = inventoryRepository;
        this.idGenerator = idGenerator;
    }

    @Override
    @Transactional
    @Auditable(
            action = "ORDER_RESERVE",
            targetType = "Order",
            targetIdExpression = "#command.orderCode")
    public void reserveForOrder(Command command) {
        for (Command.Line line : command.items()) {
            Inventory inventory =
                    inventoryRepository
                            .findBySkuAndLocation(
                                    new SkuId(line.skuCode()), new LocationId(line.locationId()))
                            .orElseThrow(
                                    () ->
                                            new InventoryNotFoundForOrderException(
                                                    line.skuCode(), line.locationId()));

            ReservationId reservationId = new ReservationId(idGenerator.nextId());
            inventory.reserve(reservationId, new Quantity(line.quantity()));
            inventoryRepository.save(inventory);
        }
        LOG.info("注文 {} の在庫引当が完了({}行)", command.orderCode(), command.items().size());
    }
}
