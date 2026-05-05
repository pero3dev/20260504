package com.example.inventory.tpl.adapter.out.persistence;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.example.inventory.commons.event.DomainEvent;
import com.example.inventory.commons.event.DomainEventPublisher;
import com.example.inventory.commons.persistence.OptimisticLockSupport;
import com.example.inventory.tpl.application.port.out.StockMovementRepository;
import com.example.inventory.tpl.domain.model.MovementStatus;
import com.example.inventory.tpl.domain.model.MovementType;
import com.example.inventory.tpl.domain.model.StockMovement;
import com.example.inventory.tpl.domain.model.StockMovementCode;
import com.example.inventory.tpl.domain.model.StockMovementId;

@Repository
public class StockMovementRepositoryImpl implements StockMovementRepository {

    private final StockMovementMapper mapper;
    private final DomainEventPublisher eventPublisher;

    public StockMovementRepositoryImpl(
            StockMovementMapper mapper, DomainEventPublisher eventPublisher) {
        this.mapper = mapper;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public Optional<StockMovement> findById(StockMovementId id) {
        StockMovementRow row = mapper.findById(id.value());
        if (row == null) return Optional.empty();
        return Optional.of(
                StockMovement.restore(
                        new StockMovementId(row.id()),
                        new StockMovementCode(row.code()),
                        row.partnerCode(),
                        row.skuCode(),
                        row.locationId(),
                        MovementType.valueOf(row.movementType()),
                        row.quantity(),
                        MovementStatus.valueOf(row.status()),
                        row.referenceCode(),
                        row.version(),
                        row.plannedAt()));
    }

    @Override
    public boolean existsByCode(StockMovementCode code) {
        return mapper.existsByCode(code.value()) > 0;
    }

    @Override
    public StockMovement save(StockMovement aggregate) {
        StockMovementRow row =
                new StockMovementRow(
                        aggregate.id().value(),
                        aggregate.code().value(),
                        aggregate.partnerCode(),
                        aggregate.skuCode(),
                        aggregate.locationId(),
                        aggregate.movementType().name(),
                        aggregate.quantity(),
                        aggregate.status().name(),
                        aggregate.referenceCode(),
                        aggregate.version() + 1,
                        aggregate.plannedAt());

        if (aggregate.version() == 0L) {
            mapper.insert(row);
        } else {
            int affected = mapper.update(row, aggregate.version());
            OptimisticLockSupport.verify(
                    affected, "StockMovement", aggregate.id().value(), aggregate.version());
        }

        for (DomainEvent event : aggregate.pendingEvents()) {
            eventPublisher.publish(event);
        }
        aggregate.clearPendingEvents();
        return aggregate;
    }

    @Override
    public void delete(StockMovement aggregate) {
        int affected = mapper.delete(aggregate.id().value(), aggregate.version());
        OptimisticLockSupport.verify(
                affected, "StockMovement", aggregate.id().value(), aggregate.version());
    }
}
