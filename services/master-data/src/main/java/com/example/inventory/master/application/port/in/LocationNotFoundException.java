package com.example.inventory.master.application.port.in;

import org.springframework.http.HttpStatus;

import com.example.inventory.commons.error.BusinessException;

public class LocationNotFoundException extends BusinessException {

    public static final String CODE = "ERR_LOCATION_NOT_FOUND";

    public LocationNotFoundException(long locationId) {
        super("Location が見つかりません: " + locationId);
    }

    @Override
    public String errorCode() {
        return CODE;
    }

    @Override
    public HttpStatus statusCode() {
        return HttpStatus.NOT_FOUND;
    }
}
