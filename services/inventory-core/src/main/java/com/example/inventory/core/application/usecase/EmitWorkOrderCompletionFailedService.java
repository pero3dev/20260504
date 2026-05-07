package com.example.inventory.core.application.usecase;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.example.inventory.commons.event.DomainEventPublisher;
import com.example.inventory.core.application.port.in.EmitWorkOrderCompletionFailedUseCase;
import com.example.inventory.core.domain.event.WorkOrderCompletionFailedEvent;

/**
 * Manufacturing WorkOrder 完成品 INBOUND 失敗の補償イベントを Outbox 経由で発行する。
 *
 * <p>{@link Propagation#REQUIRES_NEW} で別 TX。これにより Receive TX 側が rollback されても 本ユースケースの outbox
 * INSERT は独立して commit される。 {@link EmitWorkOrderConsumptionFailedService} と同設計。
 */
@Service
public class EmitWorkOrderCompletionFailedService implements EmitWorkOrderCompletionFailedUseCase {

    private final DomainEventPublisher eventPublisher;

    public EmitWorkOrderCompletionFailedService(DomainEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void emit(Command command) {
        eventPublisher.publish(
                new WorkOrderCompletionFailedEvent(
                        command.workOrderAggregateId(),
                        command.workOrderCode(),
                        command.errorCode(),
                        command.reason(),
                        command.productSkuCode(),
                        command.locationId(),
                        command.plannedQuantity(),
                        Instant.now()));
    }
}
