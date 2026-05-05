package com.example.inventory.audit.adapter.out.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.example.inventory.audit.application.port.in.ProcessAuditEventUseCase;
import com.example.inventory.audit.domain.model.AuditOutcome;
import com.example.inventory.audit.domain.model.HashHex;
import com.example.inventory.commons.tenant.TenantId;

class Sha256HashCalculatorTest {

    private final Sha256HashCalculator calculator = new Sha256HashCalculator();

    @Test
    void 同じ入力からは決定的に同じハッシュを得る() {
        ProcessAuditEventUseCase.Command cmd = sample(1001L);

        HashHex h1 = calculator.compute(HashHex.INITIAL, cmd);
        HashHex h2 = calculator.compute(HashHex.INITIAL, cmd);

        assertThat(h1).isEqualTo(h2);
    }

    @Test
    void prev_hashが違えば結果ハッシュも違う() {
        ProcessAuditEventUseCase.Command cmd = sample(1001L);

        HashHex h1 = calculator.compute(HashHex.INITIAL, cmd);
        HashHex h2 = calculator.compute(allOnes(), cmd);

        assertThat(h1).isNotEqualTo(h2);
    }

    @Test
    void payloadが違えば結果ハッシュも違う() {
        HashHex h1 = calculator.compute(HashHex.INITIAL, sample(1001L));
        HashHex h2 =
                calculator.compute(HashHex.INITIAL, sampleWithPayload(1001L, "{\"quantity\":99}"));

        assertThat(h1).isNotEqualTo(h2);
    }

    @Test
    void 出力は常に64文字のhex() {
        HashHex h = calculator.compute(HashHex.INITIAL, sample(1L));
        assertThat(h.value()).hasSize(64).matches("^[0-9a-f]{64}$");
    }

    private static ProcessAuditEventUseCase.Command sample(long eventId) {
        return sampleWithPayload(eventId, "{\"quantity\":3}");
    }

    private static ProcessAuditEventUseCase.Command sampleWithPayload(
            long eventId, String payload) {
        return new ProcessAuditEventUseCase.Command(
                new TenantId("dev"),
                eventId,
                "INVENTORY_RESERVE",
                "Inventory",
                "1",
                "user-001",
                "dev",
                AuditOutcome.SUCCESS,
                null,
                false,
                payload,
                Instant.parse("2026-05-05T10:00:00Z"));
    }

    private static HashHex allOnes() {
        return new HashHex("f".repeat(64));
    }
}
