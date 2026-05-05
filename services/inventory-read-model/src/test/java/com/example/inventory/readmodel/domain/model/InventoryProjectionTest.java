package com.example.inventory.readmodel.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class InventoryProjectionTest {

    private static InventoryProjection sample(long version) {
        return new InventoryProjection(1L, "SKU-1", "LOC-1", 7, 3, version, Instant.now());
    }

    @Test
    void 同じversionのイベントはstaleと判定する() {
        InventoryProjection p = sample(5L);
        assertThat(p.isStaleAgainst(5L)).isTrue();
    }

    @Test
    void より小さいversionのイベントもstale() {
        InventoryProjection p = sample(5L);
        assertThat(p.isStaleAgainst(4L)).isTrue();
    }

    @Test
    void より大きいversionのイベントは適用すべき() {
        InventoryProjection p = sample(5L);
        assertThat(p.isStaleAgainst(6L)).isFalse();
    }
}
