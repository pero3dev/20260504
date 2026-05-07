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
import org.mockito.Mockito;

import com.example.inventory.manufacturing.application.port.in.HandleCompletionFailureUseCase;
import com.example.inventory.manufacturing.application.port.out.WorkOrderRepository;
import com.example.inventory.manufacturing.domain.model.BomComponent;
import com.example.inventory.manufacturing.domain.model.WorkOrder;
import com.example.inventory.manufacturing.domain.model.WorkOrderCode;
import com.example.inventory.manufacturing.domain.model.WorkOrderId;
import com.example.inventory.manufacturing.domain.model.WorkOrderStatus;

class HandleCompletionFailureServiceTest {

    private WorkOrderRepository repository;
    private HandleCompletionFailureService service;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(WorkOrderRepository.class);
        service = new HandleCompletionFailureService(repository);
    }

    @Test
    void COMPLETED_状態の_WorkOrder_は_状態が変わらず_save_もされない_観測のみ() {
        WorkOrder w = newPlanned();
        w.release();
        w.complete();
        when(repository.findById(new WorkOrderId(1L))).thenReturn(Optional.of(w));

        service.handle(
                new HandleCompletionFailureUseCase.Command(
                        1L,
                        "WO-2026-0001",
                        "ERR_INVENTORY_NOT_FOUND_FOR_ORDER",
                        "完成品 SKU の在庫レコード未作成",
                        "SKU-WIDGET-X",
                        "LOC-FACTORY-A",
                        10));

        verify(repository, never()).save(any());
        assertThat(w.status()).isEqualTo(WorkOrderStatus.COMPLETED);
    }

    @Test
    void WorkOrder_が見つからなければ_スキップ_at_least_once_の冗長配信を吸収() {
        when(repository.findById(new WorkOrderId(1L))).thenReturn(Optional.empty());

        service.handle(
                new HandleCompletionFailureUseCase.Command(
                        1L,
                        "WO-2026-0001",
                        "ERR_UNKNOWN_SKU",
                        "完成品 SKU 投影未到達",
                        "SKU-WIDGET-X",
                        "LOC-FACTORY-A",
                        10));

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
