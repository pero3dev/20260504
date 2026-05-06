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

import com.example.inventory.commons.persistence.SnowflakeIdGenerator;
import com.example.inventory.core.application.port.in.ConsumeWorkOrderComponentsUseCase;
import com.example.inventory.core.application.port.in.InventoryNotFoundForOrderException;
import com.example.inventory.core.application.port.out.InventoryRepository;
import com.example.inventory.core.domain.model.InsufficientStockException;
import com.example.inventory.core.domain.model.Inventory;
import com.example.inventory.core.domain.model.InventoryId;
import com.example.inventory.core.domain.model.LocationId;
import com.example.inventory.core.domain.model.Quantity;
import com.example.inventory.core.domain.model.SkuId;

class ConsumeWorkOrderComponentsServiceTest {

    private InventoryRepository repository;
    private SnowflakeIdGenerator idGenerator;
    private ConsumeWorkOrderComponentsService service;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(InventoryRepository.class);
        idGenerator = Mockito.mock(SnowflakeIdGenerator.class);
        when(idGenerator.nextId()).thenReturn(1L, 2L, 3L);
        service = new ConsumeWorkOrderComponentsService(repository, idGenerator);
    }

    @Test
    void 各構成要素は_reserve_と_ship_を経由して_available_を_減らして_save_される() {
        Inventory invA = inv("SKU-A", 100);
        Inventory invB = inv("SKU-B", 50);
        when(repository.findBySkuAndLocation(eq(new SkuId("SKU-A")), any(LocationId.class)))
                .thenReturn(Optional.of(invA));
        when(repository.findBySkuAndLocation(eq(new SkuId("SKU-B")), any(LocationId.class)))
                .thenReturn(Optional.of(invB));

        service.consume(
                new ConsumeWorkOrderComponentsUseCase.Command(
                        999L,
                        100L,
                        "WO-2026-0001",
                        "LOC-FACTORY-A",
                        List.of(
                                new ConsumeWorkOrderComponentsUseCase.Command.Line("SKU-A", 20),
                                new ConsumeWorkOrderComponentsUseCase.Command.Line("SKU-B", 10))));

        // 構成要素の数だけ save される
        verify(repository, times(2)).save(any(Inventory.class));
        assertThat(invA.available().value()).isEqualTo(80);
        assertThat(invA.reserved().value()).isZero();
        assertThat(invB.available().value()).isEqualTo(40);
        assertThat(invB.reserved().value()).isZero();
    }

    @Test
    void 構成要素のうち_1_つでも在庫不足なら_途中で_InsufficientStockException_を_投げて_全構成要素が_ロールバック対象になる() {
        Inventory invA = inv("SKU-A", 100);
        Inventory invB = inv("SKU-B", 5);
        when(repository.findBySkuAndLocation(eq(new SkuId("SKU-A")), any(LocationId.class)))
                .thenReturn(Optional.of(invA));
        when(repository.findBySkuAndLocation(eq(new SkuId("SKU-B")), any(LocationId.class)))
                .thenReturn(Optional.of(invB));

        assertThatThrownBy(
                        () ->
                                service.consume(
                                        new ConsumeWorkOrderComponentsUseCase.Command(
                                                999L,
                                                100L,
                                                "WO-2026-0001",
                                                "LOC-FACTORY-A",
                                                List.of(
                                                        new ConsumeWorkOrderComponentsUseCase
                                                                .Command.Line("SKU-A", 20),
                                                        new ConsumeWorkOrderComponentsUseCase
                                                                .Command.Line("SKU-B", 10)))))
                .isInstanceOf(InsufficientStockException.class);

        // 単体テストは @Transactional を貼らないので 1 件目の save は呼ばれる(本番は TX
        // ロールバックでまとめて巻き戻る)。ここでは「2 件目に到達して例外が出た」ことが本質。
        verify(repository, times(1)).save(any(Inventory.class));
    }

    @Test
    void 構成要素の在庫レコードが無ければ_InventoryNotFoundForOrderException() {
        when(repository.findBySkuAndLocation(any(SkuId.class), any(LocationId.class)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                service.consume(
                                        new ConsumeWorkOrderComponentsUseCase.Command(
                                                999L,
                                                100L,
                                                "WO-2026-0001",
                                                "LOC-FACTORY-A",
                                                List.of(
                                                        new ConsumeWorkOrderComponentsUseCase
                                                                .Command.Line("SKU-MISSING", 5)))))
                .isInstanceOf(InventoryNotFoundForOrderException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void 各構成要素ごとに_reservation_用_id_を_idGenerator_から取得する() {
        Inventory invA = inv("SKU-A", 100);
        Inventory invB = inv("SKU-B", 50);
        when(repository.findBySkuAndLocation(eq(new SkuId("SKU-A")), any(LocationId.class)))
                .thenReturn(Optional.of(invA));
        when(repository.findBySkuAndLocation(eq(new SkuId("SKU-B")), any(LocationId.class)))
                .thenReturn(Optional.of(invB));

        service.consume(
                new ConsumeWorkOrderComponentsUseCase.Command(
                        999L,
                        100L,
                        "WO-2026-0001",
                        "LOC-FACTORY-A",
                        List.of(
                                new ConsumeWorkOrderComponentsUseCase.Command.Line("SKU-A", 20),
                                new ConsumeWorkOrderComponentsUseCase.Command.Line("SKU-B", 10))));

        verify(idGenerator, times(2)).nextId();
    }

    private static Inventory inv(String skuCode, int available) {
        return Inventory.restore(
                new InventoryId(100L),
                new SkuId(skuCode),
                new LocationId("LOC-FACTORY-A"),
                new Quantity(available),
                new Quantity(0),
                1L);
    }
}
