package com.example.inventory.core.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.example.inventory.core.application.port.in.InventoryNotFoundForOrderException;
import com.example.inventory.core.application.port.in.ShipForOrderUseCase;
import com.example.inventory.core.application.port.out.InventoryRepository;
import com.example.inventory.core.domain.model.InsufficientReservedException;
import com.example.inventory.core.domain.model.Inventory;
import com.example.inventory.core.domain.model.InventoryId;
import com.example.inventory.core.domain.model.LocationId;
import com.example.inventory.core.domain.model.Quantity;
import com.example.inventory.core.domain.model.SkuId;

class ShipForOrderServiceTest {

    private InventoryRepository repository;
    private ShipForOrderService service;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(InventoryRepository.class);
        service = new ShipForOrderService(repository);
    }

    @Test
    void 各明細は_reserved_を消化して_save_される() {
        // available=80, reserved=20(D9 Reserve 後の状態)から ship(20)
        Inventory invA = invWithReserved("SKU-A", 80, 20);
        Inventory invB = invWithReserved("SKU-B", 50, 10);
        when(repository.findBySkuAndLocation(eq(new SkuId("SKU-A")), any(LocationId.class)))
                .thenReturn(Optional.of(invA));
        when(repository.findBySkuAndLocation(eq(new SkuId("SKU-B")), any(LocationId.class)))
                .thenReturn(Optional.of(invB));

        service.shipForOrder(
                new ShipForOrderUseCase.Command(
                        999L,
                        100L,
                        "SO-2026-0001",
                        List.of(
                                new ShipForOrderUseCase.Command.Line(1, "SKU-A", "LOC-1", 20),
                                new ShipForOrderUseCase.Command.Line(2, "SKU-B", "LOC-1", 10))));

        verify(repository, times(2)).save(any(Inventory.class));
        // ship は reserved を減らす(available は変わらない、reserve 時に既に引いてある)
        assertThat(invA.available().value()).isEqualTo(80);
        assertThat(invA.reserved().value()).isZero();
        assertThat(invB.available().value()).isEqualTo(50);
        assertThat(invB.reserved().value()).isZero();
    }

    @Test
    void Inventoryレコードが無いと_InventoryNotFoundForOrderException() {
        when(repository.findBySkuAndLocation(any(SkuId.class), any(LocationId.class)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                service.shipForOrder(
                                        new ShipForOrderUseCase.Command(
                                                999L,
                                                100L,
                                                "SO-2026-0001",
                                                List.of(
                                                        new ShipForOrderUseCase.Command.Line(
                                                                1, "SKU-A", "LOC-1", 20)))))
                .isInstanceOf(InventoryNotFoundForOrderException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void reserved_不足なら_InsufficientReservedException_本質的にはデータ整合性破綻() {
        Inventory invA = invWithReserved("SKU-A", 80, 5);
        when(repository.findBySkuAndLocation(eq(new SkuId("SKU-A")), any(LocationId.class)))
                .thenReturn(Optional.of(invA));

        assertThatThrownBy(
                        () ->
                                service.shipForOrder(
                                        new ShipForOrderUseCase.Command(
                                                999L,
                                                100L,
                                                "SO-2026-0001",
                                                List.of(
                                                        new ShipForOrderUseCase.Command.Line(
                                                                1, "SKU-A", "LOC-1", 20)))))
                .isInstanceOf(InsufficientReservedException.class);
        verify(repository, never()).save(any());
    }

    private static Inventory invWithReserved(String skuCode, int available, int reserved) {
        return Inventory.restore(
                new InventoryId(100L),
                new SkuId(skuCode),
                new LocationId("LOC-1"),
                new Quantity(available),
                new Quantity(reserved),
                1L);
    }
}
