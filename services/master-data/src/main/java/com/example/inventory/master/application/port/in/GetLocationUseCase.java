package com.example.inventory.master.application.port.in;

import com.example.inventory.master.domain.model.Location;

public interface GetLocationUseCase {

    Location get(long locationId);
}
