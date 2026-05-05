package com.example.inventory.tpl.application.usecase;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.inventory.commons.audit.Auditable;
import com.example.inventory.commons.persistence.SnowflakeIdGenerator;
import com.example.inventory.tpl.application.port.in.DuplicateStockMovementCodeException;
import com.example.inventory.tpl.application.port.in.PlanStockMovementUseCase;
import com.example.inventory.tpl.application.port.out.StockMovementRepository;
import com.example.inventory.tpl.domain.model.StockMovement;
import com.example.inventory.tpl.domain.model.StockMovementCode;
import com.example.inventory.tpl.domain.model.StockMovementId;

@Service
public class PlanStockMovementService implements PlanStockMovementUseCase {

    private final StockMovementRepository repository;
    private final SnowflakeIdGenerator idGenerator;

    public PlanStockMovementService(
            StockMovementRepository repository, SnowflakeIdGenerator idGenerator) {
        this.repository = repository;
        this.idGenerator = idGenerator;
    }

    @Override
    @Transactional
    @Auditable(
            action = "STOCK_MOVEMENT_PLAN",
            targetType = "StockMovement",
            targetIdExpression = "#command.code")
    public StockMovement plan(Command command) {
        StockMovementCode code = new StockMovementCode(command.code());
        if (repository.existsByCode(code)) {
            throw new DuplicateStockMovementCodeException(command.code());
        }
        StockMovementId id = new StockMovementId(idGenerator.nextId());
        StockMovement movement =
                StockMovement.plan(
                        id,
                        code,
                        command.partnerCode(),
                        command.skuCode(),
                        command.locationId(),
                        command.movementType(),
                        command.quantity(),
                        command.referenceCode());
        return repository.save(movement);
    }
}
