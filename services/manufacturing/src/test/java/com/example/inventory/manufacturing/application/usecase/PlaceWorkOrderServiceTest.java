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

import com.example.inventory.commons.persistence.SnowflakeIdGenerator;
import com.example.inventory.manufacturing.application.port.in.BomNotFoundException;
import com.example.inventory.manufacturing.application.port.in.DuplicateWorkOrderCodeException;
import com.example.inventory.manufacturing.application.port.in.PlaceWorkOrderUseCase;
import com.example.inventory.manufacturing.application.port.out.BomRepository;
import com.example.inventory.manufacturing.application.port.out.WorkOrderRepository;
import com.example.inventory.manufacturing.domain.model.Bom;
import com.example.inventory.manufacturing.domain.model.BomComponent;
import com.example.inventory.manufacturing.domain.model.WorkOrder;
import com.example.inventory.manufacturing.domain.model.WorkOrderCode;
import com.example.inventory.manufacturing.domain.model.WorkOrderStatus;

class PlaceWorkOrderServiceTest {

    private WorkOrderRepository workOrderRepository;
    private BomRepository bomRepository;
    private SnowflakeIdGenerator idGenerator;
    private PlaceWorkOrderService service;

    @BeforeEach
    void setUp() {
        workOrderRepository = Mockito.mock(WorkOrderRepository.class);
        bomRepository = Mockito.mock(BomRepository.class);
        idGenerator = Mockito.mock(SnowflakeIdGenerator.class);
        when(idGenerator.nextId()).thenReturn(100L);
        when(workOrderRepository.save(any(WorkOrder.class))).thenAnswer(inv -> inv.getArgument(0));
        service = new PlaceWorkOrderService(workOrderRepository, bomRepository, idGenerator);
    }

    @Test
    void BOM_の構成をスナップショットして_PLANNED_状態の指図を作る() {
        when(workOrderRepository.existsByCode(any(WorkOrderCode.class))).thenReturn(false);
        when(bomRepository.findByProductSkuCode("SKU-WIDGET-X"))
                .thenReturn(
                        Optional.of(
                                new Bom(
                                        "SKU-WIDGET-X",
                                        List.of(
                                                new BomComponent("SKU-A", 2),
                                                new BomComponent("SKU-B", 1)))));

        WorkOrder created =
                service.place(
                        new PlaceWorkOrderUseCase.Command(
                                "WO-2026-0001", "SKU-WIDGET-X", "LOC-FACTORY-A", 10, null));

        assertThat(created.status()).isEqualTo(WorkOrderStatus.PLANNED);
        assertThat(created.components()).hasSize(2);
        assertThat(created.components().get(0).componentSkuCode()).isEqualTo("SKU-A");
        assertThat(created.components().get(0).quantityPerUnit()).isEqualTo(2);
        assertThat(created.components().get(1).componentSkuCode()).isEqualTo("SKU-B");
        // place 時はイベント未発行(release 時に発行)
        assertThat(created.pendingEvents()).isEmpty();
    }

    @Test
    void 重複_WorkOrderCode_なら_DuplicateWorkOrderCodeException() {
        when(workOrderRepository.existsByCode(any(WorkOrderCode.class))).thenReturn(true);

        assertThatThrownBy(
                        () ->
                                service.place(
                                        new PlaceWorkOrderUseCase.Command(
                                                "WO-2026-0001",
                                                "SKU-WIDGET-X",
                                                "LOC-FACTORY-A",
                                                10,
                                                null)))
                .isInstanceOf(DuplicateWorkOrderCodeException.class);
        verify(workOrderRepository, never()).save(any());
    }

    @Test
    void BOM_未登録なら_BomNotFoundException() {
        when(workOrderRepository.existsByCode(any(WorkOrderCode.class))).thenReturn(false);
        when(bomRepository.findByProductSkuCode("SKU-UNKNOWN")).thenReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                service.place(
                                        new PlaceWorkOrderUseCase.Command(
                                                "WO-2026-0001",
                                                "SKU-UNKNOWN",
                                                "LOC-FACTORY-A",
                                                10,
                                                null)))
                .isInstanceOf(BomNotFoundException.class);
        verify(workOrderRepository, never()).save(any());
    }
}
