package com.example.inventory.audit.application.usecase;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.inventory.audit.application.port.in.ProcessAuditEventUseCase;
import com.example.inventory.audit.application.port.in.VerifyAuditChainUseCase;
import com.example.inventory.audit.application.port.out.AuditChainReader;
import com.example.inventory.audit.application.port.out.HashCalculator;
import com.example.inventory.audit.domain.model.AuditRecord;
import com.example.inventory.audit.domain.model.HashHex;
import com.example.inventory.commons.tenant.TenantId;

/**
 * 監査チェーン整合性検証(ADR-0008)。
 *
 * <p>1テナント分のレコードを sequence 昇順で読み、以下を検証:
 *
 * <ul>
 *   <li>{@code sequence} が 1 から連続(SEQUENCE_GAP 検出)
 *   <li>{@code prev_hash} が直前レコードの {@code hash} と一致(PREV_HASH_MISMATCH)
 *   <li>{@code hash} を再計算して保存値と一致(HASH_MISMATCH = 改竄)
 * </ul>
 *
 * <p>検証ロジックは Audit Service の append 経路と同じ {@link HashCalculator} を使う。 計算ロジックの変更は破壊的(既存チェーンが invalid
 * 判定される)。
 */
@Service
public class AuditChainVerifier implements VerifyAuditChainUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(AuditChainVerifier.class);

    private final AuditChainReader reader;
    private final HashCalculator calculator;

    public AuditChainVerifier(AuditChainReader reader, HashCalculator calculator) {
        this.reader = reader;
        this.calculator = calculator;
    }

    @Override
    public Report verify(TenantId tenantId) {
        List<AuditRecord> records = reader.findAllOrderedBySequence(tenantId);
        if (records.isEmpty()) {
            return new Report(tenantId, 0, Status.EMPTY, List.of());
        }

        List<Mismatch> mismatches = new ArrayList<>();
        HashHex expectedPrevHash = HashHex.INITIAL;
        long expectedSeq = 1L;

        for (AuditRecord r : records) {
            if (r.sequence() != expectedSeq) {
                mismatches.add(
                        new Mismatch(
                                r.sequence(),
                                MismatchType.SEQUENCE_GAP,
                                "sequence 不連続: 期待 " + expectedSeq + " / 実際 " + r.sequence(),
                                String.valueOf(expectedSeq),
                                String.valueOf(r.sequence())));
                // ギャップを検出しても続行(以降のチェーンも検証する)
                expectedSeq = r.sequence();
            }

            if (!r.prevHash().equals(expectedPrevHash)) {
                mismatches.add(
                        new Mismatch(
                                r.sequence(),
                                MismatchType.PREV_HASH_MISMATCH,
                                "prev_hash がチェーン上の直前レコードの hash と不一致",
                                expectedPrevHash.value(),
                                r.prevHash().value()));
            }

            HashHex recomputed = calculator.compute(r.prevHash(), toCommand(r));
            if (!r.hash().equals(recomputed)) {
                mismatches.add(
                        new Mismatch(
                                r.sequence(),
                                MismatchType.HASH_MISMATCH,
                                "hash 再計算結果が保存値と不一致(改竄の可能性)",
                                recomputed.value(),
                                r.hash().value()));
            }

            expectedPrevHash = r.hash();
            expectedSeq++;
        }

        Status status = mismatches.isEmpty() ? Status.OK : Status.MISMATCH;
        if (status == Status.MISMATCH) {
            LOG.warn(
                    "audit チェーン不整合 tenant={} records={} mismatches={}",
                    tenantId.value(),
                    records.size(),
                    mismatches.size());
        }
        return new Report(tenantId, records.size(), status, mismatches);
    }

    /** AuditRecord を ProcessAuditEventUseCase.Command に変換(ハッシュ再計算用)。 */
    private static ProcessAuditEventUseCase.Command toCommand(AuditRecord r) {
        return new ProcessAuditEventUseCase.Command(
                r.tenantId(),
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
                r.occurredAt());
    }
}
