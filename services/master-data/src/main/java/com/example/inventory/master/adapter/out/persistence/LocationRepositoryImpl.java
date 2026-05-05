package com.example.inventory.master.adapter.out.persistence;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.example.inventory.commons.event.DomainEvent;
import com.example.inventory.commons.event.DomainEventPublisher;
import com.example.inventory.commons.persistence.OptimisticLockSupport;
import com.example.inventory.master.application.port.out.LocationRepository;
import com.example.inventory.master.domain.model.Location;
import com.example.inventory.master.domain.model.LocationCode;
import com.example.inventory.master.domain.model.LocationId;

@Repository
public class LocationRepositoryImpl implements LocationRepository {

    private final LocationMapper mapper;
    private final DomainEventPublisher eventPublisher;

    public LocationRepositoryImpl(LocationMapper mapper, DomainEventPublisher eventPublisher) {
        this.mapper = mapper;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public Optional<Location> findById(LocationId id) {
        LocationRow row = mapper.findById(id.value());
        if (row == null) return Optional.empty();
        return Optional.of(
                Location.restore(
                        new LocationId(row.id()),
                        new LocationCode(row.code()),
                        row.name(),
                        row.addressLine(),
                        row.locationType(),
                        row.version()));
    }

    @Override
    public boolean existsByCode(LocationCode code) {
        return mapper.existsByCode(code.value()) > 0;
    }

    @Override
    public Location save(Location aggregate) {
        LocationRow row =
                new LocationRow(
                        aggregate.id().value(),
                        aggregate.code().value(),
                        aggregate.name(),
                        aggregate.addressLine(),
                        aggregate.locationType(),
                        aggregate.version() + 1);

        if (aggregate.version() == 0L) {
            mapper.insert(row);
        } else {
            int affected = mapper.update(row, aggregate.version());
            OptimisticLockSupport.verify(
                    affected, "Location", aggregate.id().value(), aggregate.version());
        }

        for (DomainEvent event : aggregate.pendingEvents()) {
            eventPublisher.publish(event);
        }
        aggregate.clearPendingEvents();
        return aggregate;
    }

    @Override
    public void delete(Location aggregate) {
        int affected = mapper.delete(aggregate.id().value(), aggregate.version());
        OptimisticLockSupport.verify(
                affected, "Location", aggregate.id().value(), aggregate.version());
    }
}
