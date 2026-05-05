package com.example.inventory.core.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import com.example.inventory.commons.persistence.SnowflakeIdGenerator;
import com.example.inventory.core.application.port.in.ApplyStockMovementUseCase;
import com.example.inventory.core.application.port.in.InventoryNotFoundForOrderException;
import com.example.inventory.core.application.port.out.InventoryRepository;
import com.example.inventory.core.domain.model.InsufficientStockException;
import com.example.inventory.core.domain.model.Inventory;
import com.example.inventory.core.domain.model.InventoryId;
import com.example.inventory.core.domain.model.LocationId;
import com.example.inventory.core.domain.model.Quantity;
import com.example.inventory.core.domain.model.SkuId;

class ApplyStockMovementServiceTest {

    private InventoryRepository repository;
    private SnowflakeIdGenerator idGenerator;
    private ApplyStockMovementService service;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(InventoryRepository.class);
        idGenerator = Mockito.mock(SnowflakeIdGenerator.class);
        when(idGenerator.nextId()).thenReturn(1234567890L);
        service = new ApplyStockMovementService(repository, idGenerator);
    }

    @Test
    void INBOUND_は_available_を_増やして_save_される() {
        Inventory inv = inv(100, 0, 1L);
        when(repository.findBySkuAndLocation(any(SkuId.class), any(LocationId.class)))
                .thenReturn(Optional.of(inv));

        service.apply(cmd("INBOUND", 30));

        ArgumentCaptor<Inventory> captor = ArgumentCaptor.forClass(Inventory.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().available().value()).isEqualTo(130);
        assertThat(captor.getValue().reserved().value()).isZero();
    }

    @Test
    void OUTBOUND_は_reserve_と_ship_を_経由して_available_を_減らして_save_される() {
        Inventory inv = inv(100, 0, 1L);
        when(repository.findBySkuAndLocation(any(SkuId.class), any(LocationId.class)))
                .thenReturn(Optional.of(inv));

        service.apply(cmd("OUTBOUND", 30));

        ArgumentCaptor<Inventory> captor = ArgumentCaptor.forClass(Inventory.class);
        verify(repository).save(captor.capture());
        Inventory saved = captor.getValue();
        assertThat(saved.available().value()).isEqualTo(70);
        // reserve+ship を即時実行のため reserved は 0 に戻る
        assertThat(saved.reserved().value()).isZero();
    }

    @Test
    void OUTBOUND_で在庫不足なら_InsufficientStockException() {
        Inventory inv = inv(10, 0, 1L);
        when(repository.findBySkuAndLocation(any(SkuId.class), any(LocationId.class)))
                .thenReturn(Optional.of(inv));

        assertThatThrownBy(() -> service.apply(cmd("OUTBOUND", 30)))
                .isInstanceOf(InsufficientStockException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void Inventoryレコードが無いと_InventoryNotFoundForOrderException() {
        when(repository.findBySkuAndLocation(any(SkuId.class), any(LocationId.class)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.apply(cmd("INBOUND", 30)))
                .isInstanceOf(InventoryNotFoundForOrderException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void ADJUSTMENT_は_MVP_未対応で_スキップされ_save_されない() {
        Inventory inv = inv(100, 0, 1L);
        when(repository.findBySkuAndLocation(any(SkuId.class), any(LocationId.class)))
                .thenReturn(Optional.of(inv));

        service.apply(cmd("ADJUSTMENT", 5));

        verify(repository, never()).save(any());
    }

    @Test
    void 未知の_movementType_はスキップされ_save_されない() {
        Inventory inv = inv(100, 0, 1L);
        when(repository.findBySkuAndLocation(any(SkuId.class), any(LocationId.class)))
                .thenReturn(Optional.of(inv));

        service.apply(cmd("UNKNOWN", 5));

        verify(repository, never()).save(any());
    }

    @Test
    void OUTBOUND_は_reserve_用_id_を_idGenerator_から取得する() {
        Inventory inv = inv(100, 0, 1L);
        when(repository.findBySkuAndLocation(any(SkuId.class), any(LocationId.class)))
                .thenReturn(Optional.of(inv));

        service.apply(cmd("OUTBOUND", 10));

        verify(idGenerator, times(1)).nextId();
    }

    private static Inventory inv(int available, int reserved, long version) {
        return Inventory.restore(
                new InventoryId(100L),
                new SkuId("SKU-1"),
                new LocationId("LOC-1"),
                new Quantity(available),
                new Quantity(reserved),
                version);
    }

    private static ApplyStockMovementUseCase.Command cmd(String type, int qty) {
        return new ApplyStockMovementUseCase.Command(999L, "MV-001", "SKU-1", "LOC-1", type, qty);
    }
}
