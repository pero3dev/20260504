package com.example.inventory.manufacturing.application.usecase;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.inventory.commons.audit.Auditable;
import com.example.inventory.manufacturing.application.port.in.HandleCompletionFailureUseCase;
import com.example.inventory.manufacturing.application.port.out.WorkOrderRepository;
import com.example.inventory.manufacturing.domain.model.WorkOrder;
import com.example.inventory.manufacturing.domain.model.WorkOrderId;

/**
 * 完成品 INBOUND 失敗の補償を観測 + audit する。
 *
 * <p>{@link HandleConsumptionFailureService}(部品消費失敗 → cancel)とは異なり、 集約状態は触らない:
 *
 * <ul>
 *   <li>WorkOrder は既に COMPLETED(物理現実で不可逆)
 *   <li>状態を CANCELLED 等に戻すと現実と帳簿が乖離する(矛盾)
 *   <li>本サービスの仕事は **監査ログ取り + 業務側への警告** に絞る
 * </ul>
 *
 * <p>業務側で必要な是正(運用 API で {@code Inventory.receive} を再実行 or 完成品破棄登録)は別経路で扱う。
 *
 * <p>未知の workOrderId(古い指図が無い等)は無視する(at-least-once の冗長配信を吸収)。
 */
@Service
public class HandleCompletionFailureService implements HandleCompletionFailureUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(HandleCompletionFailureService.class);

    private final WorkOrderRepository workOrderRepository;

    public HandleCompletionFailureService(WorkOrderRepository workOrderRepository) {
        this.workOrderRepository = workOrderRepository;
    }

    @Override
    @Transactional
    @Auditable(
            action = "WORK_ORDER_COMPLETION_FAILED_OBSERVED",
            targetType = "WorkOrder",
            targetIdExpression = "#command.workOrderCode")
    public void handle(Command command) {
        WorkOrder workOrder =
                workOrderRepository.findById(new WorkOrderId(command.workOrderId())).orElse(null);
        if (workOrder == null) {
            LOG.warn(
                    "完成品 INBOUND 失敗の補償対象 WorkOrder が見つからずスキップ workOrderId={} workOrderCode={}",
                    command.workOrderId(),
                    command.workOrderCode());
            return;
        }
        // ⚠️ 集約状態は触らない。 COMPLETED は物理現実で不可逆なので、 帳簿と現実を一致させる作業は
        // 業務側の手動是正(運用 API で Inventory.receive 再実行 or 完成品破棄登録)に委ねる。
        LOG.warn(
                "完成品 INBOUND 失敗を観測 — 業務側で是正が必要: workOrderCode={} status={} productSku={} location={} qty={} errorCode={} reason={}",
                command.workOrderCode(),
                workOrder.status(),
                command.productSkuCode(),
                command.locationId(),
                command.plannedQuantity(),
                command.errorCode(),
                command.reason());
    }
}
