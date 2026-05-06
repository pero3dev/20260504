package com.example.inventory.manufacturing.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.example.inventory.manufacturing.application.port.in.WorkOrderNotFoundException;
import com.example.inventory.manufacturing.application.port.in.WorkOrderStateConflictException;
import com.example.inventory.manufacturing.application.port.out.WorkOrderRepository;
import com.example.inventory.manufacturing.domain.model.BomComponent;
import com.example.inventory.manufacturing.domain.model.WorkOrder;
import com.example.inventory.manufacturing.domain.model.WorkOrderCode;
import com.example.inventory.manufacturing.domain.model.WorkOrderId;
import com.example.inventory.manufacturing.domain.model.WorkOrderStatus;

class ReleaseWorkOrderServiceTest {

    private WorkOrderRepository repository;
    private ReleaseWorkOrderService service;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(WorkOrderRepository.class);
        when(repository.save(any(WorkOrder.class))).thenAnswer(inv -> inv.getArgument(0));
        service = new ReleaseWorkOrderService(repository);
    }

    @Test
    void 既存_PLANNED_指図は_RELEASED_に遷移して_save_される() {
        WorkOrder planned = newPlanned();
        when(repository.findById(new WorkOrderId(1L))).thenReturn(Optional.of(planned));

        WorkOrder result = service.release(1L);

        assertThat(result.status()).isEqualTo(WorkOrderStatus.RELEASED);
        verify(repository).save(planned);
    }

    @Test
    void 指図が見つからなければ_WorkOrderNotFoundException() {
        when(repository.findById(new WorkOrderId(1L))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.release(1L))
                .isInstanceOf(WorkOrderNotFoundException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void COMPLETED_指図に対する_release_は_WorkOrderStateConflictException_に変換される() {
        WorkOrder w = newPlanned();
        w.release();
        w.complete();
        when(repository.findById(new WorkOrderId(1L))).thenReturn(Optional.of(w));

        assertThatThrownBy(() -> service.release(1L))
                .isInstanceOf(WorkOrderStateConflictException.class);
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
