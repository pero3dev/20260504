package com.example.inventory.core.application.usecase;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.example.inventory.commons.event.DomainEventPublisher;
import com.example.inventory.core.application.port.in.EmitOrderReservationFailedUseCase;
import com.example.inventory.core.domain.event.OrderReservationFailedEvent;

/**
 * Reserve 失敗の補償イベントを Outbox 経由で発行する。
 *
 * <p>{@link Propagation#REQUIRES_NEW} で別 TX。これにより Reserve TX 側が rollback されても 本ユースケースの outbox
 * INSERT は独立して commit される。Listener から呼び出されるため self-invocation 問題は無し(別 Bean の呼び出し)。
 */
@Service
public class EmitOrderReservationFailedService implements EmitOrderReservationFailedUseCase {

    private final DomainEventPublisher eventPublisher;

    public EmitOrderReservationFailedService(DomainEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void emit(Command command) {
        eventPublisher.publish(
                new OrderReservationFailedEvent(
                        command.orderAggregateId(),
                        command.orderCode(),
                        command.errorCode(),
                        command.reason(),
                        command.failedSkuCode(),
                        command.failedLocationId(),
                        Instant.now()));
    }
}
