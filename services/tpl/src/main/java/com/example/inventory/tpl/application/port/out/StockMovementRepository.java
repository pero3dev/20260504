package com.example.inventory.tpl.application.port.out;

import java.util.Optional;

import com.example.inventory.commons.persistence.AggregateRepository;
import com.example.inventory.tpl.domain.model.StockMovement;
import com.example.inventory.tpl.domain.model.StockMovementCode;
import com.example.inventory.tpl.domain.model.StockMovementId;

public interface StockMovementRepository
        extends AggregateRepository<StockMovement, StockMovementId> {

    @Override
    Optional<StockMovement> findById(StockMovementId id);

    @Override
    StockMovement save(StockMovement aggregate);

    @Override
    void delete(StockMovement aggregate);

    boolean existsByCode(StockMovementCode code);
}
