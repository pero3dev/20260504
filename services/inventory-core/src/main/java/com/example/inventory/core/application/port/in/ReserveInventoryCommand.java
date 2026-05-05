package com.example.inventory.core.application.port.in;

/** 在庫引当コマンド。テナントは暗黙(認証フィルタが設定する {@code TenantContext} から取得)。 */
public record ReserveInventoryCommand(long inventoryId, int quantity) {

    public ReserveInventoryCommand {
        if (inventoryId <= 0) {
            throw new IllegalArgumentException("inventoryId は正の値である必要があります");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity は正の値である必要があります");
        }
    }
}
