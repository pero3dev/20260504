package com.example.inventory.tpl.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.example.inventory.tpl.domain.event.StockMovementPlannedEvent;

class StockMovementTest {

    private static final StockMovementId ID = new StockMovementId(500_000_000_001L);
    private static final StockMovementCode CODE = new StockMovementCode("SM-2026-0001");

    @Test
    void plan_は_StockMovementPlannedEvent_を発行し_PLANNED_状態() {
        StockMovement m =
                StockMovement.plan(
                        ID,
                        CODE,
                        "PT-ACME-CORP",
                        "SKU-1",
                        "LOC-WAREHOUSE-EAST",
                        MovementType.INBOUND,
                        100,
                        "ASN-12345");

        assertThat(m.status()).isEqualTo(MovementStatus.PLANNED);
        assertThat(m.movementType()).isEqualTo(MovementType.INBOUND);
        assertThat(m.quantity()).isEqualTo(100);
        assertThat(m.partnerCode()).isEqualTo("PT-ACME-CORP");
        assertThat(m.referenceCode()).isEqualTo("ASN-12345");
        assertThat(m.pendingEvents()).hasSize(1);
        assertThat(m.pendingEvents().get(0)).isInstanceOf(StockMovementPlannedEvent.class);

        StockMovementPlannedEvent ev = (StockMovementPlannedEvent) m.pendingEvents().get(0);
        assertThat(ev.aggregateId()).isEqualTo(ID.value());
        assertThat(ev.movementType()).isEqualTo("INBOUND");
        assertThat(ev.versionAfter()).isEqualTo(1L);
    }

    @Test
    void 数量0は拒否される() {
        assertThatThrownBy(
                        () ->
                                StockMovement.plan(
                                        ID,
                                        CODE,
                                        "PT-X",
                                        "SKU-1",
                                        "LOC-1",
                                        MovementType.OUTBOUND,
                                        0,
                                        null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("quantity");
    }

    @Test
    void referenceCode_は_null_でも空文字に正規化() {
        StockMovement m =
                StockMovement.plan(
                        ID, CODE, "PT-X", "SKU-1", "LOC-1", MovementType.ADJUSTMENT, 5, null);
        assertThat(m.referenceCode()).isEmpty();
    }

    @Test
    void cancel_は_PLANNED_からのみ可能で冪等() {
        StockMovement m =
                StockMovement.plan(
                        ID, CODE, "PT-X", "SKU-1", "LOC-1", MovementType.INBOUND, 1, null);
        m.cancel();
        m.cancel(); // 冪等

        assertThat(m.status()).isEqualTo(MovementStatus.CANCELLED);
    }

    @Test
    void cancel_は確定済からは不可() {
        StockMovement m =
                StockMovement.restore(
                        ID,
                        CODE,
                        "PT-X",
                        "SKU-1",
                        "LOC-1",
                        MovementType.INBOUND,
                        1,
                        MovementStatus.RECEIVED,
                        null,
                        1L,
                        java.time.Instant.now());

        assertThatThrownBy(m::cancel)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("取り消せません");
    }

    @Test
    void StockMovementCode_は大文字英数とハイフンのみ() {
        assertThatThrownBy(() -> new StockMovementCode("sm-lower"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StockMovementCode("SM"))
                .isInstanceOf(IllegalArgumentException.class);
        new StockMovementCode("SM-2026-0001");
    }
}
