package com.example.inventory.audit.adapter.in.scheduled;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.example.inventory.audit.application.port.in.ComputeDailyMerkleAnchorUseCase;
import com.example.inventory.audit.application.port.in.ComputeDailyMerkleAnchorUseCase.Result;
import com.example.inventory.audit.config.AnchorProperties;
import com.example.inventory.audit.domain.model.HashHex;
import com.example.inventory.audit.domain.model.MerkleAnchor;
import com.example.inventory.commons.tenant.TenantId;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/** {@link DailyMerkleAnchorScheduler} の動作検証(A5 follow-up²⁷)。 */
class DailyMerkleAnchorSchedulerTest {

    @Test
    void tenants_空_なら_useCase_を呼ばず_warn_のみ() {
        ComputeDailyMerkleAnchorUseCase useCase =
                Mockito.mock(ComputeDailyMerkleAnchorUseCase.class);
        AnchorProperties props = new AnchorProperties(true, List.of(), null, null);
        MeterRegistry registry = new SimpleMeterRegistry();

        new DailyMerkleAnchorScheduler(useCase, props, registry).anchorYesterdayForAllTenants();

        verify(useCase, never()).compute(any());
        assertThat(registry.find(DailyMerkleAnchorScheduler.METRIC_COMPUTE_COUNT).counters())
                .isEmpty();
        assertThat(registry.find(DailyMerkleAnchorScheduler.METRIC_COMPUTE_EXCEPTIONS).counters())
                .isEmpty();
    }

    @Test
    void 各_tenant_に対し_useCase_を_1_回ずつ呼ぶ() {
        ComputeDailyMerkleAnchorUseCase useCase =
                Mockito.mock(ComputeDailyMerkleAnchorUseCase.class);
        when(useCase.compute(any())).thenAnswer(inv -> createdResult(inv.getArgument(0)));
        AnchorProperties props =
                new AnchorProperties(true, List.of("acme", "globex", "initech"), null, null);
        MeterRegistry registry = new SimpleMeterRegistry();

        new DailyMerkleAnchorScheduler(useCase, props, registry).anchorYesterdayForAllTenants();

        verify(useCase, times(3)).compute(any());
    }

    @Test
    void created_と_already_anchored_を_status_別に_counter_発行する() {
        ComputeDailyMerkleAnchorUseCase useCase =
                Mockito.mock(ComputeDailyMerkleAnchorUseCase.class);
        when(useCase.compute(any()))
                .thenAnswer(inv -> createdResult(inv.getArgument(0))) // acme → created
                .thenAnswer(inv -> alreadyAnchoredResult(inv.getArgument(0))); // globex → existing
        AnchorProperties props = new AnchorProperties(true, List.of("acme", "globex"), null, null);
        MeterRegistry registry = new SimpleMeterRegistry();

        new DailyMerkleAnchorScheduler(useCase, props, registry).anchorYesterdayForAllTenants();

        assertThat(
                        counter(
                                registry,
                                DailyMerkleAnchorScheduler.METRIC_COMPUTE_COUNT,
                                "acme",
                                "created"))
                .isEqualTo(1.0);
        assertThat(
                        counter(
                                registry,
                                DailyMerkleAnchorScheduler.METRIC_COMPUTE_COUNT,
                                "globex",
                                "already_anchored"))
                .isEqualTo(1.0);
        // exception counter は登録されない
        assertThat(registry.find(DailyMerkleAnchorScheduler.METRIC_COMPUTE_EXCEPTIONS).counters())
                .isEmpty();
    }

    @Test
    void useCase_が_RuntimeException_を投げても_他_tenant_は_続行_して_exception_counter_発行() {
        ComputeDailyMerkleAnchorUseCase useCase =
                Mockito.mock(ComputeDailyMerkleAnchorUseCase.class);
        when(useCase.compute(any()))
                .thenThrow(new RuntimeException("Aurora 切断")) // acme で例外
                .thenAnswer(inv -> createdResult(inv.getArgument(0))); // globex は成功
        AnchorProperties props = new AnchorProperties(true, List.of("acme", "globex"), null, null);
        MeterRegistry registry = new SimpleMeterRegistry();

        new DailyMerkleAnchorScheduler(useCase, props, registry).anchorYesterdayForAllTenants();

        verify(useCase, times(2)).compute(any());
        Counter exc =
                registry.find(DailyMerkleAnchorScheduler.METRIC_COMPUTE_EXCEPTIONS)
                        .tag("tenant", "acme")
                        .counter();
        assertThat(exc).isNotNull();
        assertThat(exc.count()).isEqualTo(1.0);
        // globex は created
        assertThat(
                        counter(
                                registry,
                                DailyMerkleAnchorScheduler.METRIC_COMPUTE_COUNT,
                                "globex",
                                "created"))
                .isEqualTo(1.0);
    }

    private static double counter(
            MeterRegistry registry, String name, String tenant, String status) {
        Counter c = registry.find(name).tag("tenant", tenant).tag("status", status).counter();
        return c == null ? 0.0 : c.count();
    }

    private static Result createdResult(ComputeDailyMerkleAnchorUseCase.Command c) {
        MerkleAnchor a =
                new MerkleAnchor(
                        c.tenantId(),
                        c.anchorDate(),
                        HashHex.INITIAL,
                        0L,
                        0L,
                        0L,
                        Instant.parse("2026-05-09T01:00:00Z"));
        return new Result(a, false, Optional.empty());
    }

    private static Result alreadyAnchoredResult(ComputeDailyMerkleAnchorUseCase.Command c) {
        MerkleAnchor existing =
                new MerkleAnchor(
                        c.tenantId(),
                        c.anchorDate(),
                        HashHex.INITIAL,
                        0L,
                        0L,
                        0L,
                        Instant.parse("2026-05-08T01:00:00Z"));
        return new Result(existing, true, Optional.of(existing));
    }

    @SuppressWarnings("unused")
    private static TenantId t(String id) {
        return new TenantId(id);
    }
}
