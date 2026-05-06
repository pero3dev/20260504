package com.example.inventory.manufacturing.application.usecase;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.inventory.commons.audit.Auditable;
import com.example.inventory.manufacturing.application.port.in.ReleaseWorkOrderUseCase;
import com.example.inventory.manufacturing.application.port.in.WorkOrderNotFoundException;
import com.example.inventory.manufacturing.application.port.in.WorkOrderStateConflictException;
import com.example.inventory.manufacturing.application.port.out.WorkOrderRepository;
import com.example.inventory.manufacturing.domain.model.WorkOrder;
import com.example.inventory.manufacturing.domain.model.WorkOrderId;

/**
 * 製造指図リリース(着手指示)サービス。
 *
 * <p>状態遷移 PLANNED → RELEASED と {@code manufacturing.work_order.released.v1} の発行を 1 トランザクション内で確定する。
 * Outbox(ADR-0009)経由なので Kafka 直接発行せず、commit 後に publisher が拾う。
 */
@Service
public class ReleaseWorkOrderService implements ReleaseWorkOrderUseCase {

    private final WorkOrderRepository repository;

    public ReleaseWorkOrderService(WorkOrderRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    @Auditable(
            action = "WORK_ORDER_RELEASE",
            targetType = "WorkOrder",
            targetIdExpression = "#workOrderId")
    public WorkOrder release(long workOrderId) {
        WorkOrder workOrder =
                repository
                        .findById(new WorkOrderId(workOrderId))
                        .orElseThrow(() -> new WorkOrderNotFoundException(workOrderId));
        try {
            workOrder.release();
        } catch (IllegalStateException e) {
            // ドメイン純粋性のため domain 側は IllegalStateException を投げる。
            // 境界(application)で BusinessException に昇格させて HTTP 409 へマップ(ADR-0006)。
            throw new WorkOrderStateConflictException(e.getMessage());
        }
        return repository.save(workOrder);
    }
}
