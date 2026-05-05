package com.example.inventory.master.application.port.in;

import com.example.inventory.master.domain.model.Partner;

public interface GetPartnerUseCase {

    Partner get(long partnerId);
}
