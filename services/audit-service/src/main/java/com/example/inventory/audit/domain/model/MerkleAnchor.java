package com.example.inventory.audit.domain.model;

import java.time.Instant;
import java.time.LocalDate;

import com.example.inventory.commons.tenant.TenantId;

/**
 * 日次 Merkle anchor。テナント × 日付単位で、その日に取り込まれた監査レコード群の Merkle root を保管する(ADR-0008)。
 *
 * <p>本 anchor 自身も WORM(audit_merkle_anchor テーブルは UPDATE/DELETE 不可、トリガで強制)。本番では追加で S3 Object Lock
 * (Compliance mode)で保管すべきだが、本実装は DB 内 anchor を主な改ざん検知レイヤとし、 S3 連携はインフラ側の別タスクとする。
 *
 * <p>{@code firstSequence}, {@code lastSequence} は対象期間のレコードの sequence 範囲。 {@code recordCount =
 * lastSequence - firstSequence + 1} とは限らない(対象期間=日付の occurred_at 範囲が 厳密に sequence
 * と一致するわけではない)ため両方を独立に持つ。
 *
 * <p>{@code rootHash} は対象 anchor 範囲のレコードが空の場合 {@link HashHex#INITIAL}(全ゼロ)。
 */
public record MerkleAnchor(
        TenantId tenantId,
        LocalDate anchorDate,
        HashHex rootHash,
        long recordCount,
        long firstSequence,
        long lastSequence,
        Instant computedAt) {

    public MerkleAnchor {
        if (tenantId == null) throw new IllegalArgumentException("tenantId は必須");
        if (anchorDate == null) throw new IllegalArgumentException("anchorDate は必須");
        if (rootHash == null) throw new IllegalArgumentException("rootHash は必須");
        if (recordCount < 0) throw new IllegalArgumentException("recordCount は非負");
    }
}
