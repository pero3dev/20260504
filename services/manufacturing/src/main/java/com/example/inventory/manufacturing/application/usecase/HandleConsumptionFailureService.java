package com.example.inventory.manufacturing.application.usecase;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.inventory.commons.audit.Auditable;
import com.example.inventory.manufacturing.application.port.in.HandleConsumptionFailureUseCase;
import com.example.inventory.manufacturing.application.port.out.WorkOrderRepository;
import com.example.inventory.manufacturing.domain.model.WorkOrder;
import com.example.inventory.manufacturing.domain.model.WorkOrderId;

/**
 * 部品消費失敗の補償を受けて WorkOrder を CANCELLED に遷移させる。
 *
 * <p>未知の workOrderId(古い指図が無い等)は無視する(at-least-once の冗長配信を吸収)。 既に COMPLETED の指図に補償が来たケース(通常発生しない)も
 * IllegalState を catch してスキップする — Saga 上の整合性は別ジョブで監査する想定で、ここでは至誠処理を優先。
 *
 * <p>@Auditable で {@code WORK_ORDER_CANCEL_BY_CONSUMPTION_FAILURE} を audit.log.v1 に記録。
 */
@Service
public class HandleConsumptionFailureService implements HandleConsumptionFailureUseCase {

    private static final Logger LOG =
            LoggerFactory.getLogger(HandleConsumptionFailureService.class);

    private final WorkOrderRepository workOrderRepository;

    public HandleConsumptionFailureService(WorkOrderRepository workOrderRepository) {
        this.workOrderRepository = workOrderRepository;
    }

    @Override
    @Transactional
    @Auditable(
            action = "WORK_ORDER_CANCEL_BY_CONSUMPTION_FAILURE",
            targetType = "WorkOrder",
            targetIdExpression = "#command.workOrderCode")
    public void handle(Command command) {
        WorkOrder workOrder =
                workOrderRepository.findById(new WorkOrderId(command.workOrderId())).orElse(null);
        if (workOrder == null) {
            LOG.warn(
                    "補償対象の WorkOrder が見つからずスキップ workOrderId={} workOrderCode={}",
                    command.workOrderId(),
                    command.workOrderCode());
            return;
        }
        try {
            workOrder.cancel();
        } catch (IllegalStateException e) {
            // COMPLETED 状態への補償遅延などは整合性監査に任せ、ここではスキップ。
            LOG.warn(
                    "WorkOrder {} は cancel 不可状態のため補償スキップ: {}",
                    command.workOrderCode(),
                    e.getMessage());
            return;
        }
        workOrderRepository.save(workOrder);
        LOG.info(
                "部品消費失敗により WorkOrder {} を CANCELLED に遷移 errorCode={}",
                command.workOrderCode(),
                command.errorCode());
    }
}
