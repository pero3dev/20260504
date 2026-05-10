package com.example.inventory.audit.adapter.in.scheduled;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.example.inventory.audit.application.port.in.VerifyMerkleAnchorUseCase;
import com.example.inventory.audit.application.port.in.VerifyMerkleAnchorUseCase.Report;
import com.example.inventory.audit.application.port.in.VerifyMerkleAnchorUseCase.Status;
import com.example.inventory.audit.config.AnchorProperties;
import com.example.inventory.commons.tenant.TenantId;

/** {@link MerkleAnchorVerifyScheduler} の動作検証。 */
class MerkleAnchorVerifySchedulerTest {

    @Test
    void tenants_空_なら_useCase_を呼ばず_warn_のみ() {
        VerifyMerkleAnchorUseCase useCase = Mockito.mock(VerifyMerkleAnchorUseCase.class);
        AnchorProperties props =
                new AnchorProperties(
                        true, List.of(), null, new AnchorProperties.Verify(true, null, 7));

        new MerkleAnchorVerifyScheduler(useCase, props).verifyRecentAnchorsForAllTenants();

        verify(useCase, never()).verify(any());
    }

    @Test
    void 各_tenant_x_lookbackDays_日分_useCase_を呼ぶ() {
        VerifyMerkleAnchorUseCase useCase = Mockito.mock(VerifyMerkleAnchorUseCase.class);
        when(useCase.verify(any())).thenAnswer(inv -> okReport(inv.getArgument(0)));
        AnchorProperties props =
                new AnchorProperties(
                        true,
                        List.of("acme", "globex"),
                        null,
                        new AnchorProperties.Verify(true, null, 3));

        new MerkleAnchorVerifyScheduler(useCase, props).verifyRecentAnchorsForAllTenants();

        // 2 tenants * 3 lookback = 6 calls
        verify(useCase, times(6)).verify(any());
    }

    @Test
    void 全_status_を返しても_全_tenant_x_全日付分_verify_を呼び続ける() {
        VerifyMerkleAnchorUseCase useCase = Mockito.mock(VerifyMerkleAnchorUseCase.class);
        when(useCase.verify(any()))
                .thenAnswer(
                        inv -> {
                            VerifyMerkleAnchorUseCase.Command c = inv.getArgument(0);
                            // day-1 → OK / day-2 → ROOT_MISMATCH / day-3 → ANCHOR_NOT_FOUND
                            int dayOffset =
                                    LocalDate.now(java.time.ZoneOffset.UTC).toEpochDay()
                                                            - c.anchorDate().toEpochDay()
                                                    > 0
                                            ? (int)
                                                    (LocalDate.now(java.time.ZoneOffset.UTC)
                                                                    .toEpochDay()
                                                            - c.anchorDate().toEpochDay())
                                            : 1;
                            Status s =
                                    switch (dayOffset) {
                                        case 1 -> Status.OK;
                                        case 2 -> Status.ROOT_MISMATCH;
                                        case 3 -> Status.ANCHOR_NOT_FOUND;
                                        default -> Status.RECORD_COUNT_MISMATCH;
                                    };
                            return new Report(
                                    c.tenantId(),
                                    c.anchorDate(),
                                    s,
                                    Optional.empty(),
                                    Optional.empty(),
                                    0L);
                        });
        AnchorProperties props =
                new AnchorProperties(
                        true, List.of("acme"), null, new AnchorProperties.Verify(true, null, 4));

        new MerkleAnchorVerifyScheduler(useCase, props).verifyRecentAnchorsForAllTenants();

        // mismatch / not-found が出ても続行して全 4 日分 verify する
        verify(useCase, times(4)).verify(any());
    }

    @Test
    void useCase_が_RuntimeException_を投げても_他の_tenant_日付の_verify_が走る() {
        VerifyMerkleAnchorUseCase useCase = Mockito.mock(VerifyMerkleAnchorUseCase.class);
        when(useCase.verify(any()))
                .thenThrow(new RuntimeException("DB 切断"))
                .thenAnswer(inv -> okReport(inv.getArgument(0)));
        AnchorProperties props =
                new AnchorProperties(
                        true, List.of("acme"), null, new AnchorProperties.Verify(true, null, 3));

        new MerkleAnchorVerifyScheduler(useCase, props).verifyRecentAnchorsForAllTenants();

        // 1 回目 throw、 残り 2 回も呼ばれる(continue-on-error)
        verify(useCase, times(3)).verify(any());
    }

    @Test
    void Verify_record_は_lookbackDays_0_以下を_default_7_に補正する() {
        AnchorProperties.Verify v = new AnchorProperties.Verify(true, null, 0);
        org.assertj.core.api.Assertions.assertThat(v.lookbackDays()).isEqualTo(7);
        org.assertj.core.api.Assertions.assertThat(v.cron()).isEqualTo("0 0 2 * * *");
    }

    private static Report okReport(VerifyMerkleAnchorUseCase.Command c) {
        return new Report(
                c.tenantId(), c.anchorDate(), Status.OK, Optional.empty(), Optional.empty(), 100L);
    }

    @SuppressWarnings("unused")
    private static TenantId t(String id) {
        return new TenantId(id);
    }
}
