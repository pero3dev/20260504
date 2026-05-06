package com.example.inventory.manufacturing.application.usecase;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.inventory.commons.audit.Auditable;
import com.example.inventory.commons.persistence.SnowflakeIdGenerator;
import com.example.inventory.manufacturing.application.port.in.BomNotFoundException;
import com.example.inventory.manufacturing.application.port.in.DuplicateWorkOrderCodeException;
import com.example.inventory.manufacturing.application.port.in.PlaceWorkOrderUseCase;
import com.example.inventory.manufacturing.application.port.out.BomRepository;
import com.example.inventory.manufacturing.application.port.out.WorkOrderRepository;
import com.example.inventory.manufacturing.domain.model.Bom;
import com.example.inventory.manufacturing.domain.model.WorkOrder;
import com.example.inventory.manufacturing.domain.model.WorkOrderCode;
import com.example.inventory.manufacturing.domain.model.WorkOrderId;

/**
 * 製造指図計画サービス。
 *
 * <p>業態固有のポイント: 構成要素は Command に含めず、サーバ側で {@link BomRepository} を引いてスナップショットする。 「リクエスト時点の BOM
 * 構成」を当時の指図に焼き付け、後の BOM 改訂と独立した部品引当を可能にする。
 */
@Service
public class PlaceWorkOrderService implements PlaceWorkOrderUseCase {

    private final WorkOrderRepository workOrderRepository;
    private final BomRepository bomRepository;
    private final SnowflakeIdGenerator idGenerator;

    public PlaceWorkOrderService(
            WorkOrderRepository workOrderRepository,
            BomRepository bomRepository,
            SnowflakeIdGenerator idGenerator) {
        this.workOrderRepository = workOrderRepository;
        this.bomRepository = bomRepository;
        this.idGenerator = idGenerator;
    }

    @Override
    @Transactional
    @Auditable(
            action = "WORK_ORDER_PLACE",
            targetType = "WorkOrder",
            targetIdExpression = "#command.code")
    public WorkOrder place(Command command) {
        WorkOrderCode code = new WorkOrderCode(command.code());
        if (workOrderRepository.existsByCode(code)) {
            throw new DuplicateWorkOrderCodeException(command.code());
        }
        Bom bom =
                bomRepository
                        .findByProductSkuCode(command.productSkuCode())
                        .orElseThrow(() -> new BomNotFoundException(command.productSkuCode()));

        WorkOrderId id = new WorkOrderId(idGenerator.nextId());
        WorkOrder workOrder =
                WorkOrder.place(
                        id,
                        code,
                        command.productSkuCode(),
                        command.locationId(),
                        command.plannedQuantity(),
                        bom.components(),
                        command.plannedStartDate());
        return workOrderRepository.save(workOrder);
    }
}
