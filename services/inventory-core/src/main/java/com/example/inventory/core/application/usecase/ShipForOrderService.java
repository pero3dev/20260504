package com.example.inventory.core.application.usecase;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.inventory.commons.audit.Auditable;
import com.example.inventory.core.application.port.in.InventoryNotFoundForOrderException;
import com.example.inventory.core.application.port.in.ShipForOrderUseCase;
import com.example.inventory.core.application.port.out.InventoryRepository;
import com.example.inventory.core.domain.model.Inventory;
import com.example.inventory.core.domain.model.LocationId;
import com.example.inventory.core.domain.model.Quantity;
import com.example.inventory.core.domain.model.SkuId;

/**
 * Wholesale 等の出荷確定イベントを受けて、各明細の {@code Inventory.ship} を呼ぶ。
 *
 * <p>Reserve は前段で実施済(reserved に乗っている前提)。本クラスでは reserved → 系外 への遷移のみ行う。
 *
 * <p>1 メッセージ = 1 トランザクション。途中の {@code InsufficientReservedException}(ありえないはず) や {@code
 * InventoryNotFoundForOrderException}(プロジェクション破綻)は @Transactional でロールバックし、 失敗は DLQ で観察する(ADR-0017
 * の方針 — Reserve+Ship 路線は MVP 補償なし)。
 */
@Service
public class ShipForOrderService implements ShipForOrderUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(ShipForOrderService.class);

    private final InventoryRepository inventoryRepository;

    public ShipForOrderService(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    @Override
    @Transactional
    @Auditable(
            action = "ORDER_SHIP",
            targetType = "Order",
            targetIdExpression = "#command.orderCode")
    public void shipForOrder(Command command) {
        for (Command.Line line : command.items()) {
            Inventory inventory =
                    inventoryRepository
                            .findBySkuAndLocation(
                                    new SkuId(line.skuCode()), new LocationId(line.locationId()))
                            .orElseThrow(
                                    () ->
                                            new InventoryNotFoundForOrderException(
                                                    line.skuCode(), line.locationId()));

            inventory.ship(new Quantity(line.quantity()));
            inventoryRepository.save(inventory);
        }
        LOG.info("注文 {} の出荷確定を反映({}行)", command.orderCode(), command.items().size());
    }
}
