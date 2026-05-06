package com.example.inventory.core.application.usecase;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.example.inventory.commons.event.DomainEventPublisher;
import com.example.inventory.core.application.port.in.EmitWorkOrderConsumptionFailedUseCase;
import com.example.inventory.core.domain.event.WorkOrderConsumptionFailedEvent;

/**
 * Manufacturing WorkOrder 部品消費失敗の補償イベントを Outbox 経由で発行する。
 *
 * <p>{@link Propagation#REQUIRES_NEW} で別 TX。これにより Consume TX 側が rollback されても 本ユースケースの outbox
 * INSERT は独立して commit される。Wholesale 用 {@code EmitWholesaleReservationFailedService} と同設計。
 */
@Service
public class EmitWorkOrderConsumptionFailedService
        implements EmitWorkOrderConsumptionFailedUseCase {

    private final DomainEventPublisher eventPublisher;

    public EmitWorkOrderConsumptionFailedService(DomainEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void emit(Command command) {
        eventPublisher.publish(
                new WorkOrderConsumptionFailedEvent(
                        command.workOrderAggregateId(),
                        command.workOrderCode(),
                        command.errorCode(),
                        command.reason(),
                        command.failedComponentSkuCode(),
                        command.failedLocationId(),
                        Instant.now()));
    }
}
