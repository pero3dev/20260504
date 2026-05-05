package com.example.inventory.readmodel.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import com.example.inventory.readmodel.application.port.out.InventoryProjectionStore;
import com.example.inventory.readmodel.domain.model.InventoryProjection;

class ApplyInventoryMovementServiceTest {

    private InventoryProjectionStore store;
    private ApplyInventoryMovementService service;

    @BeforeEach
    void setUp() {
        store = Mockito.mock(InventoryProjectionStore.class);
        service = new ApplyInventoryMovementService(store);
    }

    @Test
    void 投影が無いときは新規作成して保存する() {
        when(store.findById(1L)).thenReturn(Optional.empty());

        service.applyReserved(
                1L, "SKU-1", "LOC-1", 7, 3, 1L, Instant.parse("2026-05-04T10:00:00Z"));

        ArgumentCaptor<InventoryProjection> captor =
                ArgumentCaptor.forClass(InventoryProjection.class);
        verify(store).save(captor.capture());
        InventoryProjection saved = captor.getValue();
        assertThat(saved.id()).isEqualTo(1L);
        assertThat(saved.available()).isEqualTo(7);
        assertThat(saved.reserved()).isEqualTo(3);
        assertThat(saved.version()).isEqualTo(1L);
    }

    @Test
    void 既存より新しいversionのイベントは保存される() {
        InventoryProjection existing =
                new InventoryProjection(1L, "SKU-1", "LOC-1", 10, 0, 1L, Instant.now());
        when(store.findById(1L)).thenReturn(Optional.of(existing));

        service.applyReserved(1L, "SKU-1", "LOC-1", 7, 3, 2L, Instant.now());

        verify(store).save(any());
    }

    @Test
    void 既存と同じかそれ以下のversionのイベントは保存されない_冪等() {
        InventoryProjection existing =
                new InventoryProjection(1L, "SKU-1", "LOC-1", 7, 3, 5L, Instant.now());
        when(store.findById(1L)).thenReturn(Optional.of(existing));

        service.applyReserved(1L, "SKU-1", "LOC-1", 7, 3, 5L, Instant.now());
        service.applyReserved(1L, "SKU-1", "LOC-1", 7, 3, 4L, Instant.now());

        verify(store, never()).save(any());
    }

    @Test
    void 同じイベントを複数回適用しても投影は1回しか書かれない() {
        // 状態モックを Map で管理して realistic に再現
        Map<Long, InventoryProjection> state = new HashMap<>();
        when(store.findById(1L)).thenAnswer(inv -> Optional.ofNullable(state.get(1L)));
        Mockito.doAnswer(
                        inv -> {
                            InventoryProjection p = inv.getArgument(0);
                            state.put(p.id(), p);
                            return null;
                        })
                .when(store)
                .save(any());

        // 1回目: 新規作成
        service.applyReserved(1L, "SKU-1", "LOC-1", 7, 3, 1L, Instant.now());
        // 2回目: 同じ version → stale でスキップ
        service.applyReserved(1L, "SKU-1", "LOC-1", 7, 3, 1L, Instant.now());

        verify(store, Mockito.times(1)).save(any());
        assertThat(state.get(1L).version()).isEqualTo(1L);
    }
}
