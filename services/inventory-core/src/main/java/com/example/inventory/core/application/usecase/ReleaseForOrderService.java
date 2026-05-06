package com.example.inventory.core.application.usecase;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.inventory.commons.audit.Auditable;
import com.example.inventory.core.application.port.in.InventoryNotFoundForOrderException;
import com.example.inventory.core.application.port.in.ReleaseForOrderUseCase;
import com.example.inventory.core.application.port.out.InventoryRepository;
import com.example.inventory.core.domain.model.Inventory;
import com.example.inventory.core.domain.model.LocationId;
import com.example.inventory.core.domain.model.Quantity;
import com.example.inventory.core.domain.model.SkuId;

/**
 * 業態系の注文キャンセルイベントを受けて、各明細の {@code Inventory.release} を呼ぶ。
 *
 * <p>Reserve は前段で実施済(reserved に乗っている前提)。本クラスでは reserved → available への 戻し処理のみ行う。
 *
 * <p>1 メッセージ = 1 トランザクション。途中の {@code InsufficientReservedException}(あり得ないはず) や {@code
 * InventoryNotFoundForOrderException}(プロジェクション破綻)は @Transactional でロールバックし、 失敗は DLQ で観察する。
 */
@Service
public class ReleaseForOrderService implements ReleaseForOrderUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(ReleaseForOrderService.class);

    private final InventoryRepository inventoryRepository;

    public ReleaseForOrderService(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    @Override
    @Transactional
    @Auditable(
            action = "ORDER_RELEASE",
            targetType = "Order",
            targetIdExpression = "#command.orderCode")
    public void releaseForOrder(Command command) {
        for (Command.Line line : command.items()) {
            Inventory inventory =
                    inventoryRepository
                            .findBySkuAndLocation(
                                    new SkuId(line.skuCode()), new LocationId(line.locationId()))
                            .orElseThrow(
                                    () ->
                                            new InventoryNotFoundForOrderException(
                                                    line.skuCode(), line.locationId()));

            inventory.release(new Quantity(line.quantity()));
            inventoryRepository.save(inventory);
        }
        LOG.info("注文 {} の引当解放を反映({}行)", command.orderCode(), command.items().size());
    }
}
