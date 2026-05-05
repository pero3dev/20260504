package com.example.inventory.master.application.port.out;

import java.util.Optional;

import com.example.inventory.commons.persistence.AggregateRepository;
import com.example.inventory.master.domain.model.Partner;
import com.example.inventory.master.domain.model.PartnerCode;
import com.example.inventory.master.domain.model.PartnerId;

public interface PartnerRepository extends AggregateRepository<Partner, PartnerId> {

    @Override
    Optional<Partner> findById(PartnerId id);

    @Override
    Partner save(Partner aggregate);

    @Override
    void delete(Partner aggregate);

    boolean existsByCode(PartnerCode code);
}
