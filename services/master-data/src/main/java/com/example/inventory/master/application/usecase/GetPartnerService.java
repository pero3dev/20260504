package com.example.inventory.master.application.usecase;

import org.springframework.stereotype.Service;

import com.example.inventory.master.application.port.in.GetPartnerUseCase;
import com.example.inventory.master.application.port.in.PartnerNotFoundException;
import com.example.inventory.master.application.port.out.PartnerRepository;
import com.example.inventory.master.domain.model.Partner;
import com.example.inventory.master.domain.model.PartnerId;

@Service
public class GetPartnerService implements GetPartnerUseCase {

    private final PartnerRepository repository;

    public GetPartnerService(PartnerRepository repository) {
        this.repository = repository;
    }

    @Override
    public Partner get(long partnerId) {
        return repository
                .findById(new PartnerId(partnerId))
                .orElseThrow(() -> new PartnerNotFoundException(partnerId));
    }
}
