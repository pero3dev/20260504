package com.example.inventory.audit.application.port.in;

import java.time.LocalDate;
import java.util.Optional;

import com.example.inventory.audit.domain.model.MerkleAnchor;
import com.example.inventory.commons.tenant.TenantId;

/**
 * 1 テナント × 1 日付の Merkle anchor を計算して保管する(ADR-0008)。
 *
 * <p>対象は {@code anchorDate} の UTC 範囲(00:00:00 ≤ occurred_at < 翌日 00:00:00)に該当するレコード群。
 *
 * <p>冪等性: 既存 anchor が同 (tenant, date) で存在する場合は {@link Result#alreadyAnchored()} = true で 既存 anchor
 * を返す(WORM 保護)。再計算が必要な場合は {@link VerifyMerkleAnchorUseCase} で差異を検出する。
 *
 * <p>対象レコードが空の場合も anchor は作成する(rootHash = HashHex.INITIAL、recordCount = 0)。
 * これにより「その日には監査イベントが無かった」ことの証跡が残る。
 */
public interface ComputeDailyMerkleAnchorUseCase {

    Result compute(Command command);

    record Command(TenantId tenantId, LocalDate anchorDate) {

        public Command {
            if (tenantId == null) throw new IllegalArgumentException("tenantId は必須");
            if (anchorDate == null) throw new IllegalArgumentException("anchorDate は必須");
        }
    }

    record Result(MerkleAnchor anchor, boolean alreadyAnchored, Optional<MerkleAnchor> existing) {}
}
