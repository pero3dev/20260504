package com.example.inventory.audit.application.port.in;

import java.util.List;

import com.example.inventory.commons.tenant.TenantId;

/**
 * 入力ポート: 指定テナントの監査チェーンの整合性を検証する。
 *
 * <p>検証項目:
 *
 * <ol>
 *   <li>sequence が 1 から連続(欠番が無い)
 *   <li>各レコードの {@code prev_hash} が直前レコードの {@code hash} と一致
 *   <li>各レコードの {@code hash} を再計算して保存値と一致(改竄検知の本体)
 * </ol>
 *
 * <p>結果は {@link Report} に集約。1つでも不一致があれば {@code Status.MISMATCH}。 Mismatch 詳細は {@link Mismatch}
 * の配列で返す(運用が原因調査に使う)。
 */
public interface VerifyAuditChainUseCase {

    Report verify(TenantId tenantId);

    enum Status {
        OK,
        MISMATCH,
        EMPTY
    }

    record Report(
            TenantId tenantId, int recordsScanned, Status status, List<Mismatch> mismatches) {}

    /**
     * 不整合の1件分。{@code reason} は人間可読、{@code expected} と {@code actual} は hash mismatch / prev_hash
     * mismatch のときのみ詰める(seq 不連続では null)。
     */
    record Mismatch(
            long sequence, MismatchType type, String reason, String expected, String actual) {}

    enum MismatchType {
        SEQUENCE_GAP,
        PREV_HASH_MISMATCH,
        HASH_MISMATCH
    }
}
