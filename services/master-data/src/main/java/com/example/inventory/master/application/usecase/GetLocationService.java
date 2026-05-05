package com.example.inventory.master.application.usecase;

import org.springframework.stereotype.Service;

import com.example.inventory.master.application.port.in.GetLocationUseCase;
import com.example.inventory.master.application.port.in.LocationNotFoundException;
import com.example.inventory.master.application.port.out.LocationRepository;
import com.example.inventory.master.domain.model.Location;
import com.example.inventory.master.domain.model.LocationId;

@Service
public class GetLocationService implements GetLocationUseCase {

    private final LocationRepository repository;

    public GetLocationService(LocationRepository repository) {
        this.repository = repository;
    }

    @Override
    public Location get(long locationId) {
        return repository
                .findById(new LocationId(locationId))
                .orElseThrow(() -> new LocationNotFoundException(locationId));
    }
}
