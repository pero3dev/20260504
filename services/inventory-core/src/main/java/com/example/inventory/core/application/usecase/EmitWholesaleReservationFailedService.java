package com.example.inventory.core.application.usecase;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.example.inventory.commons.event.DomainEventPublisher;
import com.example.inventory.core.application.port.in.EmitWholesaleReservationFailedUseCase;
import com.example.inventory.core.domain.event.WholesaleReservationFailedEvent;

/**
 * Wholesale Reserve 失敗の補償イベントを Outbox 経由で発行する。
 *
 * <p>{@link Propagation#REQUIRES_NEW} で別 TX。これにより Reserve TX 側が rollback されても 本ユースケースの outbox
 * INSERT は独立して commit される。Retail/EC 用 {@code EmitOrderReservationFailedService} と 同設計。トピックのみ業態別に分離。
 */
@Service
public class EmitWholesaleReservationFailedService
        implements EmitWholesaleReservationFailedUseCase {

    private final DomainEventPublisher eventPublisher;

    public EmitWholesaleReservationFailedService(DomainEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void emit(Command command) {
        eventPublisher.publish(
                new WholesaleReservationFailedEvent(
                        command.orderAggregateId(),
                        command.orderCode(),
                        command.errorCode(),
                        command.reason(),
                        command.failedSkuCode(),
                        command.failedLocationId(),
                        Instant.now()));
    }
}
