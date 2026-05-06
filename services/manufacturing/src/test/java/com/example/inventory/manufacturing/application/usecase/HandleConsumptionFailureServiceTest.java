package com.example.inventory.manufacturing.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import com.example.inventory.manufacturing.application.port.in.HandleConsumptionFailureUseCase;
import com.example.inventory.manufacturing.application.port.out.WorkOrderRepository;
import com.example.inventory.manufacturing.domain.model.BomComponent;
import com.example.inventory.manufacturing.domain.model.WorkOrder;
import com.example.inventory.manufacturing.domain.model.WorkOrderCode;
import com.example.inventory.manufacturing.domain.model.WorkOrderId;
import com.example.inventory.manufacturing.domain.model.WorkOrderStatus;

class HandleConsumptionFailureServiceTest {

    private WorkOrderRepository repository;
    private HandleConsumptionFailureService service;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(WorkOrderRepository.class);
        when(repository.save(any(WorkOrder.class))).thenAnswer(inv -> inv.getArgument(0));
        service = new HandleConsumptionFailureService(repository);
    }

    @Test
    void RELEASED_状態の_WorkOrder_は_CANCELLED_に遷移して_save_される() {
        WorkOrder w = newPlanned();
        w.release();
        when(repository.findById(new WorkOrderId(1L))).thenReturn(Optional.of(w));

        service.handle(
                new HandleConsumptionFailureUseCase.Command(
                        1L, "WO-2026-0001", "ERR_INVENTORY_INSUFFICIENT", "在庫不足"));

        ArgumentCaptor<WorkOrder> captor = ArgumentCaptor.forClass(WorkOrder.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(WorkOrderStatus.CANCELLED);
    }

    @Test
    void PLANNED_状態の_WorkOrder_も_CANCELLED_に遷移できる_補償の早送り配信に備える() {
        WorkOrder w = newPlanned();
        when(repository.findById(new WorkOrderId(1L))).thenReturn(Optional.of(w));

        service.handle(
                new HandleConsumptionFailureUseCase.Command(
                        1L, "WO-2026-0001", "ERR_INVENTORY_INSUFFICIENT", "在庫不足"));

        ArgumentCaptor<WorkOrder> captor = ArgumentCaptor.forClass(WorkOrder.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(WorkOrderStatus.CANCELLED);
    }

    @Test
    void WorkOrder_が見つからなければ_save_されず_スキップ() {
        when(repository.findById(new WorkOrderId(1L))).thenReturn(Optional.empty());

        service.handle(
                new HandleConsumptionFailureUseCase.Command(
                        1L, "WO-2026-0001", "ERR_INVENTORY_INSUFFICIENT", "在庫不足"));

        verify(repository, never()).save(any());
    }

    @Test
    void すでに_CANCELLED_の_WorkOrder_は_冪等で_状態は_CANCELLED_のまま() {
        WorkOrder w = newPlanned();
        w.cancel();
        when(repository.findById(new WorkOrderId(1L))).thenReturn(Optional.of(w));

        service.handle(
                new HandleConsumptionFailureUseCase.Command(
                        1L, "WO-2026-0001", "ERR_INVENTORY_INSUFFICIENT", "在庫不足"));

        ArgumentCaptor<WorkOrder> captor = ArgumentCaptor.forClass(WorkOrder.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(WorkOrderStatus.CANCELLED);
    }

    @Test
    void すでに_COMPLETED_の_WorkOrder_に対する補償は_整合性監査に任せて_スキップ_save_されない() {
        WorkOrder w = newPlanned();
        w.release();
        w.complete();
        when(repository.findById(new WorkOrderId(1L))).thenReturn(Optional.of(w));

        service.handle(
                new HandleConsumptionFailureUseCase.Command(
                        1L, "WO-2026-0001", "ERR_INVENTORY_INSUFFICIENT", "在庫不足"));

        verify(repository, never()).save(any());
    }

    private static WorkOrder newPlanned() {
        return WorkOrder.place(
                new WorkOrderId(1L),
                new WorkOrderCode("WO-2026-0001"),
                "SKU-WIDGET-X",
                "LOC-FACTORY-A",
                10,
                List.of(new BomComponent("SKU-A", 2)),
                null);
    }
}
