package com.example.inventory.audit.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.example.inventory.audit.adapter.out.security.Sha256HashCalculator;
import com.example.inventory.audit.application.port.in.ProcessAuditEventUseCase;
import com.example.inventory.audit.application.port.in.VerifyAuditChainUseCase;
import com.example.inventory.audit.application.port.out.AuditChainReader;
import com.example.inventory.audit.domain.model.AuditOutcome;
import com.example.inventory.audit.domain.model.AuditRecord;
import com.example.inventory.audit.domain.model.HashHex;
import com.example.inventory.commons.tenant.TenantId;

/** AuditChainVerifier の検証シナリオ。実 Sha256HashCalculator を使ってチェーン構築 → 検証する。 */
class AuditChainVerifierTest {

    private static final TenantId TENANT = new TenantId("dev");

    private AuditChainReader reader;
    private final Sha256HashCalculator calculator = new Sha256HashCalculator();
    private AuditChainVerifier verifier;

    @BeforeEach
    void setUp() {
        reader = Mockito.mock(AuditChainReader.class);
        verifier = new AuditChainVerifier(reader, calculator);
    }

    @Test
    void 空テナントはEMPTY() {
        Mockito.when(reader.findAllOrderedBySequence(TENANT)).thenReturn(List.of());

        VerifyAuditChainUseCase.Report report = verifier.verify(TENANT);

        assertThat(report.status()).isEqualTo(VerifyAuditChainUseCase.Status.EMPTY);
        assertThat(report.recordsScanned()).isZero();
        assertThat(report.mismatches()).isEmpty();
    }

    @Test
    void 正規のチェーンはOK() {
        List<AuditRecord> chain = buildValidChain(5);
        Mockito.when(reader.findAllOrderedBySequence(TENANT)).thenReturn(chain);

        VerifyAuditChainUseCase.Report report = verifier.verify(TENANT);

        assertThat(report.status()).isEqualTo(VerifyAuditChainUseCase.Status.OK);
        assertThat(report.recordsScanned()).isEqualTo(5);
        assertThat(report.mismatches()).isEmpty();
    }

    @Test
    void hash改竄を検出する() {
        List<AuditRecord> chain = buildValidChain(3);
        // 真ん中のレコードの hash を別物に差し替え(改竄シミュレーション)
        AuditRecord tampered = withHash(chain.get(1), new HashHex("a".repeat(64)));
        chain.set(1, tampered);

        Mockito.when(reader.findAllOrderedBySequence(TENANT)).thenReturn(chain);

        VerifyAuditChainUseCase.Report report = verifier.verify(TENANT);

        assertThat(report.status()).isEqualTo(VerifyAuditChainUseCase.Status.MISMATCH);
        // tampered レコード(seq=2)で HASH_MISMATCH、その後 seq=3 で PREV_HASH_MISMATCH
        assertThat(report.mismatches())
                .extracting(VerifyAuditChainUseCase.Mismatch::type)
                .contains(VerifyAuditChainUseCase.MismatchType.HASH_MISMATCH);
    }

    @Test
    void sequenceの欠番を検出する() {
        List<AuditRecord> chain = buildValidChain(3);
        // seq=2 を削除して 1 → 3 にする(削除シミュレーション)
        chain.remove(1);

        Mockito.when(reader.findAllOrderedBySequence(TENANT)).thenReturn(chain);

        VerifyAuditChainUseCase.Report report = verifier.verify(TENANT);

        assertThat(report.status()).isEqualTo(VerifyAuditChainUseCase.Status.MISMATCH);
        assertThat(report.mismatches())
                .extracting(VerifyAuditChainUseCase.Mismatch::type)
                .contains(VerifyAuditChainUseCase.MismatchType.SEQUENCE_GAP);
        // 削除後の現 seq=3 は元 seq=2 の hash を期待するが実際は seq=3 のものなので
        // PREV_HASH_MISMATCH も同時検出される(追加情報として価値ある)
        assertThat(report.mismatches())
                .extracting(VerifyAuditChainUseCase.Mismatch::type)
                .contains(VerifyAuditChainUseCase.MismatchType.PREV_HASH_MISMATCH);
    }

    @Test
    void prev_hashの不整合を検出する() {
        List<AuditRecord> chain = buildValidChain(3);
        // seq=2 の prev_hash を不正な値に書き換える(チェーンリンク改竄)
        AuditRecord broken = withPrevHash(chain.get(1), new HashHex("b".repeat(64)));
        chain.set(1, broken);

        Mockito.when(reader.findAllOrderedBySequence(TENANT)).thenReturn(chain);

        VerifyAuditChainUseCase.Report report = verifier.verify(TENANT);

        assertThat(report.status()).isEqualTo(VerifyAuditChainUseCase.Status.MISMATCH);
        assertThat(report.mismatches())
                .extracting(VerifyAuditChainUseCase.Mismatch::type)
                .contains(VerifyAuditChainUseCase.MismatchType.PREV_HASH_MISMATCH);
    }

    /** 有効なチェーンを n 件構築。実ハッシュ計算を使い、正しい prev_hash 連鎖を作る。 */
    private List<AuditRecord> buildValidChain(int n) {
        List<AuditRecord> chain = new ArrayList<>(n);
        HashHex prev = HashHex.INITIAL;
        for (long seq = 1; seq <= n; seq++) {
            ProcessAuditEventUseCase.Command cmd = sampleCommand(seq);
            HashHex hash = calculator.compute(prev, cmd);
            chain.add(
                    new AuditRecord(
                            cmd.tenantId(),
                            seq,
                            cmd.eventId(),
                            cmd.action(),
                            cmd.targetType(),
                            cmd.targetId(),
                            cmd.operatorUserId(),
                            cmd.operatorTenantId(),
                            cmd.outcome(),
                            cmd.errorCode(),
                            cmd.readOnly(),
                            cmd.payloadJson(),
                            cmd.occurredAt(),
                            prev,
                            hash));
            prev = hash;
        }
        return chain;
    }

    private static ProcessAuditEventUseCase.Command sampleCommand(long seq) {
        return new ProcessAuditEventUseCase.Command(
                TENANT,
                1000L + seq,
                "INVENTORY_RESERVE",
                "Inventory",
                "1",
                "user-001",
                "dev",
                AuditOutcome.SUCCESS,
                null,
                false,
                "{\"seq\":" + seq + "}",
                Instant.parse("2026-05-05T10:00:00Z").plusSeconds(seq));
    }

    private static AuditRecord withHash(AuditRecord r, HashHex newHash) {
        return new AuditRecord(
                r.tenantId(),
                r.sequence(),
                r.eventId(),
                r.action(),
                r.targetType(),
                r.targetId(),
                r.operatorUserId(),
                r.operatorTenantId(),
                r.outcome(),
                r.errorCode(),
                r.readOnly(),
                r.payloadJson(),
                r.occurredAt(),
                r.prevHash(),
                newHash);
    }

    private static AuditRecord withPrevHash(AuditRecord r, HashHex newPrev) {
        return new AuditRecord(
                r.tenantId(),
                r.sequence(),
                r.eventId(),
                r.action(),
                r.targetType(),
                r.targetId(),
                r.operatorUserId(),
                r.operatorTenantId(),
                r.outcome(),
                r.errorCode(),
                r.readOnly(),
                r.payloadJson(),
                r.occurredAt(),
                newPrev,
                r.hash());
    }
}
