package com.example.inventory.core.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.example.inventory.core.application.port.in.InventoryNotFoundForOrderException;
import com.example.inventory.core.application.port.in.ReceiveFinishedGoodsUseCase;
import com.example.inventory.core.application.port.out.InventoryRepository;
import com.example.inventory.core.domain.model.Inventory;
import com.example.inventory.core.domain.model.InventoryId;
import com.example.inventory.core.domain.model.LocationId;
import com.example.inventory.core.domain.model.Quantity;
import com.example.inventory.core.domain.model.SkuId;

class ReceiveFinishedGoodsServiceTest {

    private InventoryRepository repository;
    private ReceiveFinishedGoodsService service;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(InventoryRepository.class);
        service = new ReceiveFinishedGoodsService(repository);
    }

    @Test
    void 完成品_SKU_の_Inventory_に_receive_を呼んで_available_を_増やして_save_する() {
        Inventory inv = inv("SKU-WIDGET-X", 0, 0); // 完成品在庫はゼロから始まる
        when(repository.findBySkuAndLocation(eq(new SkuId("SKU-WIDGET-X")), any(LocationId.class)))
                .thenReturn(Optional.of(inv));

        service.receive(
                new ReceiveFinishedGoodsUseCase.Command(
                        999L, 100L, "WO-2026-0001", "SKU-WIDGET-X", "LOC-FACTORY-A", 10));

        assertThat(inv.available().value()).isEqualTo(10);
        assertThat(inv.reserved().value()).isZero();
        verify(repository).save(inv);
    }

    @Test
    void 完成品_SKU_の_Inventory_レコードが無ければ_InventoryNotFoundForOrderException_DLQ() {
        when(repository.findBySkuAndLocation(any(SkuId.class), any(LocationId.class)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                service.receive(
                                        new ReceiveFinishedGoodsUseCase.Command(
                                                999L,
                                                100L,
                                                "WO-2026-0001",
                                                "SKU-UNKNOWN",
                                                "LOC-FACTORY-A",
                                                10)))
                .isInstanceOf(InventoryNotFoundForOrderException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void 既に在庫がある場合は_既存数量に加算される() {
        Inventory inv = inv("SKU-WIDGET-X", 50, 5);
        when(repository.findBySkuAndLocation(eq(new SkuId("SKU-WIDGET-X")), any(LocationId.class)))
                .thenReturn(Optional.of(inv));

        service.receive(
                new ReceiveFinishedGoodsUseCase.Command(
                        999L, 100L, "WO-2026-0001", "SKU-WIDGET-X", "LOC-FACTORY-A", 10));

        assertThat(inv.available().value()).isEqualTo(60); // 50 + 10
        assertThat(inv.reserved().value()).isEqualTo(5); // 不変
        verify(repository).save(inv);
    }

    private static Inventory inv(String skuCode, int available, int reserved) {
        return Inventory.restore(
                new InventoryId(100L),
                new SkuId(skuCode),
                new LocationId("LOC-FACTORY-A"),
                new Quantity(available),
                new Quantity(reserved),
                1L);
    }
}
