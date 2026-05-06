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
import com.example.inventory.core.application.port.in.ReleaseForOrderUseCase;
import com.example.inventory.core.application.port.out.InventoryRepository;
import com.example.inventory.core.domain.model.InsufficientReservedException;
import com.example.inventory.core.domain.model.Inventory;
import com.example.inventory.core.domain.model.InventoryId;
import com.example.inventory.core.domain.model.LocationId;
import com.example.inventory.core.domain.model.Quantity;
import com.example.inventory.core.domain.model.SkuId;

class ReleaseForOrderServiceTest {

    private InventoryRepository repository;
    private ReleaseForOrderService service;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(InventoryRepository.class);
        service = new ReleaseForOrderService(repository);
    }

    @Test
    void 各明細は_reserved_を_available_に戻して_save_される() {
        // available=80, reserved=20 から release(20) → available=100, reserved=0
        Inventory invA = invWithReserved("SKU-A", 80, 20);
        Inventory invB = invWithReserved("SKU-B", 50, 10);
        when(repository.findBySkuAndLocation(eq(new SkuId("SKU-A")), any(LocationId.class)))
                .thenReturn(Optional.of(invA));
        when(repository.findBySkuAndLocation(eq(new SkuId("SKU-B")), any(LocationId.class)))
                .thenReturn(Optional.of(invB));

        service.releaseForOrder(
                new ReleaseForOrderUseCase.Command(
                        999L,
                        100L,
                        "ORD-2026-0001",
                        List.of(
                                new ReleaseForOrderUseCase.Command.Line(1, "SKU-A", "LOC-1", 20),
                                new ReleaseForOrderUseCase.Command.Line(2, "SKU-B", "LOC-1", 10))));

        verify(repository, times(2)).save(any(Inventory.class));
        // ship と異なり release は available 増やす
        assertThat(invA.available().value()).isEqualTo(100);
        assertThat(invA.reserved().value()).isZero();
        assertThat(invB.available().value()).isEqualTo(60);
        assertThat(invB.reserved().value()).isZero();
    }

    @Test
    void Inventory_レコードが無いと_InventoryNotFoundForOrderException() {
        when(repository.findBySkuAndLocation(any(SkuId.class), any(LocationId.class)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                service.releaseForOrder(
                                        new ReleaseForOrderUseCase.Command(
                                                999L,
                                                100L,
                                                "ORD-001",
                                                List.of(
                                                        new ReleaseForOrderUseCase.Command.Line(
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
                                service.releaseForOrder(
                                        new ReleaseForOrderUseCase.Command(
                                                999L,
                                                100L,
                                                "ORD-001",
                                                List.of(
                                                        new ReleaseForOrderUseCase.Command.Line(
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
