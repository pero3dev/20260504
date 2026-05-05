package com.example.inventory.core.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.example.inventory.core.domain.event.InventoryReservedEvent;

/** Inventory 集約の単体テスト。 */
class InventoryTest {

    private static final InventoryId ID = new InventoryId(100_000_000_001L);
    private static final SkuId SKU = new SkuId("SKU-001");
    private static final LocationId LOC = new LocationId("LOC-001");

    @Test
    void 引当成功時はavailableからreservedへ移動しイベントが発行される() {
        Inventory inventory = Inventory.create(ID, SKU, LOC, new Quantity(10));

        ReservationId reservationId = inventory.reserve(new ReservationId(42L), new Quantity(3));

        assertThat(inventory.available()).isEqualTo(new Quantity(7));
        assertThat(inventory.reserved()).isEqualTo(new Quantity(3));
        assertThat(reservationId).isEqualTo(new ReservationId(42L));
        assertThat(inventory.pendingEvents()).hasSize(1);
        assertThat(inventory.pendingEvents().get(0)).isInstanceOf(InventoryReservedEvent.class);
    }

    @Test
    void 利用可能数量を超える要求は拒否される() {
        Inventory inventory = Inventory.create(ID, SKU, LOC, new Quantity(2));

        assertThatThrownBy(() -> inventory.reserve(new ReservationId(43L), new Quantity(5)))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("利用可能=2")
                .hasMessageContaining("要求=5");

        assertThat(inventory.available()).isEqualTo(new Quantity(2));
        assertThat(inventory.reserved()).isEqualTo(Quantity.ZERO);
        assertThat(inventory.pendingEvents()).isEmpty();
    }
}
