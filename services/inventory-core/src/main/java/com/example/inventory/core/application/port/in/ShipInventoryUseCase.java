package com.example.inventory.core.application.port.in;

public interface ShipInventoryUseCase {

    /** 引当済み在庫を出荷で消化する。 */
    void ship(ShipInventoryCommand command);
}
