package com.example.inventory.master.application.port.out;

import java.util.Optional;

import com.example.inventory.commons.persistence.AggregateRepository;
import com.example.inventory.master.domain.model.Location;
import com.example.inventory.master.domain.model.LocationCode;
import com.example.inventory.master.domain.model.LocationId;

public interface LocationRepository extends AggregateRepository<Location, LocationId> {

    @Override
    Optional<Location> findById(LocationId id);

    @Override
    Location save(Location aggregate);

    @Override
    void delete(Location aggregate);

    /** 同テナント内で Location コードの重複検出に使用。 */
    boolean existsByCode(LocationCode code);
}
