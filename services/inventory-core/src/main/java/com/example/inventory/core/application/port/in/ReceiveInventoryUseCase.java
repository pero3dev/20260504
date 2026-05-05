package com.example.inventory.core.application.port.in;

public interface ReceiveInventoryUseCase {

    /** 入荷で利用可能在庫を増やす。 */
    void receive(ReceiveInventoryCommand command);
}
