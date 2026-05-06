package com.example.inventory.manufacturing.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.inventory.commons.event.DomainEvent;
import com.example.inventory.manufacturing.domain.event.WorkOrderCompletedEvent;
import com.example.inventory.manufacturing.domain.event.WorkOrderReleasedEvent;

class WorkOrderTest {

    @Test
    void place_は_PLANNED_状態で_イベント未発行() {
        WorkOrder w = newPlannedWorkOrder();

        assertThat(w.status()).isEqualTo(WorkOrderStatus.PLANNED);
        assertThat(w.pendingEvents()).isEmpty();
        assertThat(w.releasedAt()).isNull();
    }

    @Test
    void
            release_は_RELEASED_に遷移し_BOM_スナップショットを_quantityPerUnit_x_plannedQuantity_に展開した_WorkOrderReleasedEvent_を発行する() {
        WorkOrder w = newPlannedWorkOrder();

        w.release();

        assertThat(w.status()).isEqualTo(WorkOrderStatus.RELEASED);
        assertThat(w.releasedAt()).isNotNull();
        List<DomainEvent> events = w.pendingEvents();
        assertThat(events).hasSize(1);
        WorkOrderReleasedEvent ev = (WorkOrderReleasedEvent) events.get(0);
        assertThat(ev.productSkuCode()).isEqualTo("SKU-WIDGET-X");
        assertThat(ev.plannedQuantity()).isEqualTo(10);
        // plannedQuantity=10, BOM={A x2, B x1} → required = {A:20, B:10}
        assertThat(ev.components()).hasSize(2);
        assertThat(ev.components().get(0).componentSkuCode()).isEqualTo("SKU-A");
        assertThat(ev.components().get(0).requiredQuantity()).isEqualTo(20);
        assertThat(ev.components().get(1).componentSkuCode()).isEqualTo("SKU-B");
        assertThat(ev.components().get(1).requiredQuantity()).isEqualTo(10);
    }

    @Test
    void 同じ_PLANNED_指図に_release_を_2_回呼んでも_2_回目は_冪等で_イベントは_1_回だけ() {
        WorkOrder w = newPlannedWorkOrder();
        w.release();
        w.clearPendingEvents();

        w.release(); // 既に RELEASED → no-op

        assertThat(w.status()).isEqualTo(WorkOrderStatus.RELEASED);
        assertThat(w.pendingEvents()).isEmpty();
    }

    @Test
    void COMPLETED_状態の指図に_release_は_IllegalState() {
        WorkOrder w = newPlannedWorkOrder();
        w.release();
        w.complete();

        assertThatThrownBy(w::release).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void complete_は_RELEASED_状態のみ可で_PLANNED_からは_IllegalState() {
        WorkOrder w = newPlannedWorkOrder();

        assertThatThrownBy(w::complete).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void complete_は_COMPLETED_に遷移し_完成品入庫イベント_WorkOrderCompletedEvent_を_plannedQuantity_で発行する() {
        WorkOrder w = newPlannedWorkOrder();
        w.release();
        w.clearPendingEvents(); // release イベントを除外

        w.complete();

        assertThat(w.status()).isEqualTo(WorkOrderStatus.COMPLETED);
        assertThat(w.completedAt()).isNotNull();
        List<DomainEvent> events = w.pendingEvents();
        assertThat(events).hasSize(1);
        WorkOrderCompletedEvent ev = (WorkOrderCompletedEvent) events.get(0);
        assertThat(ev.productSkuCode()).isEqualTo("SKU-WIDGET-X");
        assertThat(ev.locationId()).isEqualTo("LOC-FACTORY-A");
        assertThat(ev.completedQuantity()).isEqualTo(10);
    }

    @Test
    void 同じ_RELEASED_指図に_complete_を_2_回呼んでも_2_回目は_冪等で_イベントは_1_回だけ() {
        WorkOrder w = newPlannedWorkOrder();
        w.release();
        w.complete();
        w.clearPendingEvents();

        w.complete(); // 既に COMPLETED → no-op

        assertThat(w.status()).isEqualTo(WorkOrderStatus.COMPLETED);
        assertThat(w.pendingEvents()).isEmpty();
    }

    @Test
    void cancel_は_PLANNED_RELEASED_から可だが_COMPLETED_からは_IllegalState() {
        WorkOrder w = newPlannedWorkOrder();
        w.cancel();
        assertThat(w.status()).isEqualTo(WorkOrderStatus.CANCELLED);

        WorkOrder w2 = newPlannedWorkOrder();
        w2.release();
        w2.cancel();
        assertThat(w2.status()).isEqualTo(WorkOrderStatus.CANCELLED);

        WorkOrder w3 = newPlannedWorkOrder();
        w3.release();
        w3.complete();
        assertThatThrownBy(w3::cancel).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void cancel_は_冪等() {
        WorkOrder w = newPlannedWorkOrder();
        w.cancel();
        w.cancel();
        assertThat(w.status()).isEqualTo(WorkOrderStatus.CANCELLED);
    }

    @Test
    void plannedQuantity_が_ゼロ以下なら_place_は_IllegalArgument() {
        assertThatThrownBy(
                        () ->
                                WorkOrder.place(
                                        new WorkOrderId(1L),
                                        new WorkOrderCode("WO-001"),
                                        "SKU-WIDGET-X",
                                        "LOC-FACTORY-A",
                                        0,
                                        List.of(new BomComponent("SKU-A", 1)),
                                        null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 構成要素が空なら_place_は_IllegalArgument() {
        assertThatThrownBy(
                        () ->
                                WorkOrder.place(
                                        new WorkOrderId(1L),
                                        new WorkOrderCode("WO-001"),
                                        "SKU-WIDGET-X",
                                        "LOC-FACTORY-A",
                                        10,
                                        List.of(),
                                        null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void WorkOrderCode_は_3文字以上の_大文字英数とハイフンのみ() {
        assertThatThrownBy(() -> new WorkOrderCode("wo"))
                .isInstanceOf(IllegalArgumentException.class);
        new WorkOrderCode("WO-001");
    }

    @Test
    void BomComponent_の_quantityPerUnit_は_正の値必須() {
        assertThatThrownBy(() -> new BomComponent("SKU-A", 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static WorkOrder newPlannedWorkOrder() {
        return WorkOrder.place(
                new WorkOrderId(1L),
                new WorkOrderCode("WO-2026-0001"),
                "SKU-WIDGET-X",
                "LOC-FACTORY-A",
                10,
                List.of(new BomComponent("SKU-A", 2), new BomComponent("SKU-B", 1)),
                LocalDate.of(2026, 6, 1));
    }
}
