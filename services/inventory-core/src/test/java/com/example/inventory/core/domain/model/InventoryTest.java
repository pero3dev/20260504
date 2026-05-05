package com.example.inventory.core.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.example.inventory.core.domain.event.InventoryReceivedEvent;
import com.example.inventory.core.domain.event.InventoryReservedEvent;
import com.example.inventory.core.domain.event.InventoryShippedEvent;

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

    @Test
    void 入荷でavailableが増えInventoryReceivedEventが出る() {
        Inventory inventory = Inventory.create(ID, SKU, LOC, new Quantity(5));

        inventory.receive(new Quantity(3));

        assertThat(inventory.available()).isEqualTo(new Quantity(8));
        assertThat(inventory.reserved()).isEqualTo(Quantity.ZERO);
        assertThat(inventory.pendingEvents()).hasSize(1);
        assertThat(inventory.pendingEvents().get(0)).isInstanceOf(InventoryReceivedEvent.class);

        InventoryReceivedEvent ev = (InventoryReceivedEvent) inventory.pendingEvents().get(0);
        assertThat(ev.quantityReceived()).isEqualTo(3);
        assertThat(ev.availableAfter()).isEqualTo(8);
    }

    @Test
    void 入荷数量0は拒否される() {
        Inventory inventory = Inventory.create(ID, SKU, LOC, new Quantity(5));

        assertThatThrownBy(() -> inventory.receive(Quantity.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("受入数量");
    }

    @Test
    void 出荷でreservedが減りInventoryShippedEventが出る() {
        Inventory inventory = Inventory.create(ID, SKU, LOC, new Quantity(10));
        inventory.reserve(new ReservationId(99L), new Quantity(4));
        inventory.clearPendingEvents(); // reserve イベントは検証対象外

        inventory.ship(new Quantity(3));

        assertThat(inventory.available()).isEqualTo(new Quantity(6)); // available は変わらない
        assertThat(inventory.reserved()).isEqualTo(new Quantity(1)); // 4 → 1
        assertThat(inventory.pendingEvents()).hasSize(1);
        assertThat(inventory.pendingEvents().get(0)).isInstanceOf(InventoryShippedEvent.class);

        InventoryShippedEvent ev = (InventoryShippedEvent) inventory.pendingEvents().get(0);
        assertThat(ev.quantityShipped()).isEqualTo(3);
        assertThat(ev.reservedAfter()).isEqualTo(1);
    }

    @Test
    void 引当済を超える出荷は拒否される() {
        Inventory inventory = Inventory.create(ID, SKU, LOC, new Quantity(10));
        inventory.reserve(new ReservationId(99L), new Quantity(2));
        inventory.clearPendingEvents();

        assertThatThrownBy(() -> inventory.ship(new Quantity(5)))
                .isInstanceOf(InsufficientReservedException.class)
                .hasMessageContaining("引当済=2")
                .hasMessageContaining("要求=5");

        assertThat(inventory.reserved()).isEqualTo(new Quantity(2));
        assertThat(inventory.pendingEvents()).isEmpty();
    }
}
