package com.example.inventory.core.application.usecase;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.inventory.commons.audit.Auditable;
import com.example.inventory.core.application.port.in.InventoryNotFoundForOrderException;
import com.example.inventory.core.application.port.in.ReceiveFinishedGoodsUseCase;
import com.example.inventory.core.application.port.out.InventoryRepository;
import com.example.inventory.core.domain.model.Inventory;
import com.example.inventory.core.domain.model.LocationId;
import com.example.inventory.core.domain.model.Quantity;
import com.example.inventory.core.domain.model.SkuId;

/**
 * Manufacturing の WorkOrder 完了を受けて完成品 SKU の在庫を入庫する。
 *
 * <p>1 イベント = 1 完成品 SKU = 1 トランザクション = 1 Inventory.receive 呼出。 部品消費の {@code
 * ConsumeWorkOrderComponentsService}(D10)と対称的に、こちらは完成品入庫を担う。
 *
 * <p>完成品 SKU の Inventory レコードが存在しない場合(master.product.v1 投影未到達)は {@link
 * InventoryNotFoundForOrderException} で @Transactional ロールバック → DLQ で観察。
 */
@Service
public class ReceiveFinishedGoodsService implements ReceiveFinishedGoodsUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(ReceiveFinishedGoodsService.class);

    private final InventoryRepository inventoryRepository;

    public ReceiveFinishedGoodsService(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    @Override
    @Transactional
    @Auditable(
            action = "WORK_ORDER_FINISHED_GOODS_RECEIVE",
            targetType = "WorkOrder",
            targetIdExpression = "#command.workOrderCode")
    public void receive(Command command) {
        Inventory inventory =
                inventoryRepository
                        .findBySkuAndLocation(
                                new SkuId(command.productSkuCode()),
                                new LocationId(command.locationId()))
                        .orElseThrow(
                                () ->
                                        new InventoryNotFoundForOrderException(
                                                command.productSkuCode(), command.locationId()));

        inventory.receive(new Quantity(command.quantity()));
        inventoryRepository.save(inventory);

        LOG.info(
                "WorkOrder {} の完成品入庫を反映({} ×{} @ {})",
                command.workOrderCode(),
                command.productSkuCode(),
                command.quantity(),
                command.locationId());
    }
}
