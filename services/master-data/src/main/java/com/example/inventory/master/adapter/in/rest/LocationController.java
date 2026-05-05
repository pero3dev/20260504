package com.example.inventory.master.adapter.in.rest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.example.inventory.master.adapter.in.rest.api.LocationsApi;
import com.example.inventory.master.adapter.in.rest.api.model.CreateLocationRequest;
import com.example.inventory.master.adapter.in.rest.api.model.LocationResponse;
import com.example.inventory.master.application.port.in.CreateLocationUseCase;
import com.example.inventory.master.application.port.in.GetLocationUseCase;
import com.example.inventory.master.domain.model.Location;

/** Location REST 入力アダプタ。OpenAPI スキーマから生成された {@link LocationsApi} を実装する(ADR-0006)。 */
@RestController
public class LocationController implements LocationsApi {

    private final CreateLocationUseCase createLocation;
    private final GetLocationUseCase getLocation;

    public LocationController(
            CreateLocationUseCase createLocation, GetLocationUseCase getLocation) {
        this.createLocation = createLocation;
        this.getLocation = getLocation;
    }

    @Override
    public ResponseEntity<LocationResponse> createLocation(CreateLocationRequest request) {
        Location created =
                createLocation.create(
                        new CreateLocationUseCase.Command(
                                request.getCode(),
                                request.getName(),
                                request.getAddressLine(),
                                request.getLocationType()));
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(created));
    }

    @Override
    public ResponseEntity<LocationResponse> getLocation(Long locationId) {
        return ResponseEntity.ok(toResponse(getLocation.get(locationId)));
    }

    private static LocationResponse toResponse(Location location) {
        LocationResponse r = new LocationResponse();
        r.setId(location.id().value());
        r.setCode(location.code().value());
        r.setName(location.name());
        r.setAddressLine(location.addressLine());
        r.setLocationType(location.locationType());
        r.setVersion(location.version() + 1);
        return r;
    }
}
