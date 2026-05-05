package com.example.inventory.master.application.usecase;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.inventory.commons.audit.Auditable;
import com.example.inventory.commons.persistence.SnowflakeIdGenerator;
import com.example.inventory.master.application.port.in.CreateLocationUseCase;
import com.example.inventory.master.application.port.in.DuplicateLocationCodeException;
import com.example.inventory.master.application.port.out.LocationRepository;
import com.example.inventory.master.domain.model.Location;
import com.example.inventory.master.domain.model.LocationCode;
import com.example.inventory.master.domain.model.LocationId;

@Service
public class CreateLocationService implements CreateLocationUseCase {

    private final LocationRepository repository;
    private final SnowflakeIdGenerator idGenerator;

    public CreateLocationService(LocationRepository repository, SnowflakeIdGenerator idGenerator) {
        this.repository = repository;
        this.idGenerator = idGenerator;
    }

    @Override
    @Transactional
    @Auditable(
            action = "LOCATION_CREATE",
            targetType = "Location",
            targetIdExpression = "#command.code")
    public Location create(Command command) {
        LocationCode code = new LocationCode(command.code());
        if (repository.existsByCode(code)) {
            throw new DuplicateLocationCodeException(command.code());
        }
        LocationId id = new LocationId(idGenerator.nextId());
        Location location =
                Location.create(
                        id, code, command.name(), command.addressLine(), command.locationType());
        return repository.save(location);
    }
}
