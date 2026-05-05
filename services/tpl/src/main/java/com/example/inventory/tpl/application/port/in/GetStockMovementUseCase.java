package com.example.inventory.tpl.application.port.in;

import com.example.inventory.tpl.domain.model.StockMovement;

public interface GetStockMovementUseCase {

    StockMovement get(long id);
}
