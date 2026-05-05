package com.example.inventory.tpl.application.usecase;

import org.springframework.stereotype.Service;

import com.example.inventory.tpl.application.port.in.GetStockMovementUseCase;
import com.example.inventory.tpl.application.port.in.StockMovementNotFoundException;
import com.example.inventory.tpl.application.port.out.StockMovementRepository;
import com.example.inventory.tpl.domain.model.StockMovement;
import com.example.inventory.tpl.domain.model.StockMovementId;

@Service
public class GetStockMovementService implements GetStockMovementUseCase {

    private final StockMovementRepository repository;

    public GetStockMovementService(StockMovementRepository repository) {
        this.repository = repository;
    }

    @Override
    public StockMovement get(long id) {
        return repository
                .findById(new StockMovementId(id))
                .orElseThrow(() -> new StockMovementNotFoundException(id));
    }
}
