package com.example.inventory.master.application.usecase;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.inventory.commons.audit.Auditable;
import com.example.inventory.commons.persistence.SnowflakeIdGenerator;
import com.example.inventory.master.application.port.in.CreatePartnerUseCase;
import com.example.inventory.master.application.port.in.DuplicatePartnerCodeException;
import com.example.inventory.master.application.port.out.PartnerRepository;
import com.example.inventory.master.domain.model.Partner;
import com.example.inventory.master.domain.model.PartnerCode;
import com.example.inventory.master.domain.model.PartnerId;

@Service
public class CreatePartnerService implements CreatePartnerUseCase {

    private final PartnerRepository repository;
    private final SnowflakeIdGenerator idGenerator;

    public CreatePartnerService(PartnerRepository repository, SnowflakeIdGenerator idGenerator) {
        this.repository = repository;
        this.idGenerator = idGenerator;
    }

    @Override
    @Transactional
    @Auditable(
            action = "PARTNER_CREATE",
            targetType = "Partner",
            targetIdExpression = "#command.code")
    public Partner create(Command command) {
        PartnerCode code = new PartnerCode(command.code());
        if (repository.existsByCode(code)) {
            throw new DuplicatePartnerCodeException(command.code());
        }
        PartnerId id = new PartnerId(idGenerator.nextId());
        Partner partner =
                Partner.create(
                        id, code, command.name(), command.partnerType(), command.contactEmail());
        return repository.save(partner);
    }
}
