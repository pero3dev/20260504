package com.example.inventory.master.application.port.in;

import org.springframework.http.HttpStatus;

import com.example.inventory.commons.error.BusinessException;

public class PartnerNotFoundException extends BusinessException {

    public static final String CODE = "ERR_PARTNER_NOT_FOUND";

    public PartnerNotFoundException(long partnerId) {
        super("Partner が見つかりません: " + partnerId);
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
