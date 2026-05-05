package com.example.inventory.master.application.usecase;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.inventory.commons.audit.Auditable;
import com.example.inventory.commons.persistence.SnowflakeIdGenerator;
import com.example.inventory.master.application.port.in.CreateSkuUseCase;
import com.example.inventory.master.application.port.in.DuplicateSkuCodeException;
import com.example.inventory.master.application.port.out.SkuRepository;
import com.example.inventory.master.domain.model.Sku;
import com.example.inventory.master.domain.model.SkuCode;
import com.example.inventory.master.domain.model.SkuId;

@Service
public class CreateSkuService implements CreateSkuUseCase {

    private final SkuRepository repository;
    private final SnowflakeIdGenerator idGenerator;

    public CreateSkuService(SkuRepository repository, SnowflakeIdGenerator idGenerator) {
        this.repository = repository;
        this.idGenerator = idGenerator;
    }

    @Override
    @Transactional
    @Auditable(action = "SKU_CREATE", targetType = "Sku", targetIdExpression = "#command.code")
    public Sku create(Command command) {
        SkuCode code = new SkuCode(command.code());
        if (repository.existsByCode(code)) {
            throw new DuplicateSkuCodeException(command.code());
        }
        SkuId id = new SkuId(idGenerator.nextId());
        Sku sku =
                Sku.create(
                        id, code, command.name(), command.description(), command.unitOfMeasure());
        return repository.save(sku);
    }
}
