package com.example.inventory.core.application.port.in;

/** 出荷コマンド。引当済(reserved)の数量を消化する。 */
public record ShipInventoryCommand(long inventoryId, int quantity) {

    public ShipInventoryCommand {
        if (inventoryId <= 0) {
            throw new IllegalArgumentException("inventoryId は正の値である必要があります");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity は正の値である必要があります");
        }
    }
}
