package com.example.inventory.master.adapter.out.persistence;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.example.inventory.commons.event.DomainEvent;
import com.example.inventory.commons.event.DomainEventPublisher;
import com.example.inventory.commons.persistence.OptimisticLockSupport;
import com.example.inventory.master.application.port.out.PartnerRepository;
import com.example.inventory.master.domain.model.Partner;
import com.example.inventory.master.domain.model.PartnerCode;
import com.example.inventory.master.domain.model.PartnerId;

@Repository
public class PartnerRepositoryImpl implements PartnerRepository {

    private final PartnerMapper mapper;
    private final DomainEventPublisher eventPublisher;

    public PartnerRepositoryImpl(PartnerMapper mapper, DomainEventPublisher eventPublisher) {
        this.mapper = mapper;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public Optional<Partner> findById(PartnerId id) {
        PartnerRow row = mapper.findById(id.value());
        if (row == null) return Optional.empty();
        return Optional.of(
                Partner.restore(
                        new PartnerId(row.id()),
                        new PartnerCode(row.code()),
                        row.name(),
                        row.partnerType(),
                        row.contactEmail(),
                        row.version()));
    }

    @Override
    public boolean existsByCode(PartnerCode code) {
        return mapper.existsByCode(code.value()) > 0;
    }

    @Override
    public Partner save(Partner aggregate) {
        PartnerRow row =
                new PartnerRow(
                        aggregate.id().value(),
                        aggregate.code().value(),
                        aggregate.name(),
                        aggregate.partnerType(),
                        aggregate.contactEmail(),
                        aggregate.version() + 1);

        if (aggregate.version() == 0L) {
            mapper.insert(row);
        } else {
            int affected = mapper.update(row, aggregate.version());
            OptimisticLockSupport.verify(
                    affected, "Partner", aggregate.id().value(), aggregate.version());
        }

        for (DomainEvent event : aggregate.pendingEvents()) {
            eventPublisher.publish(event);
        }
        aggregate.clearPendingEvents();
        return aggregate;
    }

    @Override
    public void delete(Partner aggregate) {
        int affected = mapper.delete(aggregate.id().value(), aggregate.version());
        OptimisticLockSupport.verify(
                affected, "Partner", aggregate.id().value(), aggregate.version());
    }
}
