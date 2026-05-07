package com.example.inventory.core.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import com.example.inventory.commons.event.DomainEventPublisher;
import com.example.inventory.core.application.port.in.EmitWorkOrderCompletionFailedUseCase;
import com.example.inventory.core.domain.event.WorkOrderCompletionFailedEvent;

class EmitWorkOrderCompletionFailedServiceTest {

    private DomainEventPublisher publisher;
    private EmitWorkOrderCompletionFailedService service;

    @BeforeEach
    void setUp() {
        publisher = Mockito.mock(DomainEventPublisher.class);
        service = new EmitWorkOrderCompletionFailedService(publisher);
    }

    @Test
    void Command_の各値が_WorkOrderCompletionFailedEvent_に詰められて_publish_される() {
        service.emit(
                new EmitWorkOrderCompletionFailedUseCase.Command(
                        7001L,
                        "WO-2026-0001",
                        "ERR_INVENTORY_NOT_FOUND_FOR_ORDER",
                        "完成品 SKU の在庫レコード未作成",
                        "SKU-WIDGET-X",
                        "LOC-FACTORY-A",
                        10));

        ArgumentCaptor<WorkOrderCompletionFailedEvent> captor =
                ArgumentCaptor.forClass(WorkOrderCompletionFailedEvent.class);
        verify(publisher).publish(captor.capture());
        WorkOrderCompletionFailedEvent event = captor.getValue();
        assertThat(event.aggregateId()).isEqualTo(7001L);
        assertThat(event.workOrderCode()).isEqualTo("WO-2026-0001");
        assertThat(event.errorCode()).isEqualTo("ERR_INVENTORY_NOT_FOUND_FOR_ORDER");
        assertThat(event.reason()).isEqualTo("完成品 SKU の在庫レコード未作成");
        assertThat(event.productSkuCode()).isEqualTo("SKU-WIDGET-X");
        assertThat(event.locationId()).isEqualTo("LOC-FACTORY-A");
        assertThat(event.plannedQuantity()).isEqualTo(10);
        assertThat(event.occurredAt()).isNotNull();
        assertThat(event.topic()).isEqualTo("manufacturing.completion.failed.v1");
        assertThat(event.schemaVersion()).isEqualTo("1.0");
    }
}
