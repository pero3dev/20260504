package com.example.inventory.master.adapter.out.persistence;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.example.inventory.commons.event.DomainEvent;
import com.example.inventory.commons.event.DomainEventPublisher;
import com.example.inventory.commons.persistence.OptimisticLockSupport;
import com.example.inventory.master.application.port.out.SkuRepository;
import com.example.inventory.master.domain.model.Sku;
import com.example.inventory.master.domain.model.SkuCode;
import com.example.inventory.master.domain.model.SkuId;

@Repository
public class SkuRepositoryImpl implements SkuRepository {

    private final SkuMapper mapper;
    private final DomainEventPublisher eventPublisher;

    public SkuRepositoryImpl(SkuMapper mapper, DomainEventPublisher eventPublisher) {
        this.mapper = mapper;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public Optional<Sku> findById(SkuId id) {
        SkuRow row = mapper.findById(id.value());
        if (row == null) return Optional.empty();
        return Optional.of(
                Sku.restore(
                        new SkuId(row.id()),
                        new SkuCode(row.code()),
                        row.name(),
                        row.description(),
                        row.unitOfMeasure(),
                        row.version()));
    }

    @Override
    public boolean existsByCode(SkuCode code) {
        return mapper.existsByCode(code.value()) > 0;
    }

    @Override
    public Sku save(Sku aggregate) {
        SkuRow row =
                new SkuRow(
                        aggregate.id().value(),
                        aggregate.code().value(),
                        aggregate.name(),
                        aggregate.description(),
                        aggregate.unitOfMeasure(),
                        aggregate.version() + 1);

        if (aggregate.version() == 0L) {
            mapper.insert(row);
        } else {
            int affected = mapper.update(row, aggregate.version());
            OptimisticLockSupport.verify(
                    affected, "Sku", aggregate.id().value(), aggregate.version());
        }

        for (DomainEvent event : aggregate.pendingEvents()) {
            eventPublisher.publish(event);
        }
        aggregate.clearPendingEvents();
        return aggregate;
    }

    @Override
    public void delete(Sku aggregate) {
        int affected = mapper.delete(aggregate.id().value(), aggregate.version());
        OptimisticLockSupport.verify(affected, "Sku", aggregate.id().value(), aggregate.version());
    }
}
