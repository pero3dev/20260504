package com.example.inventory.audit.application.port.in;

import java.time.LocalDate;
import java.util.Optional;

import com.example.inventory.audit.domain.model.HashHex;
import com.example.inventory.audit.domain.model.MerkleAnchor;
import com.example.inventory.commons.tenant.TenantId;

/**
 * 既存の Merkle anchor を再計算して整合性検証する(ADR-0008)。
 *
 * <p>用途:
 *
 * <ul>
 *   <li>monthly compliance audit: 過去の anchor が改ざん検知できることを定期確認
 *   <li>incident response: 監査ログが信頼できるか確認
 * </ul>
 *
 * <p>{@code expectedRoot}(保存値)と {@code recomputedRoot}(再計算値)が不一致なら ROOT_MISMATCH。 同一なら OK。anchor
 * 自体が無ければ ANCHOR_NOT_FOUND。
 */
public interface VerifyMerkleAnchorUseCase {

    Report verify(Command command);

    record Command(TenantId tenantId, LocalDate anchorDate) {

        public Command {
            if (tenantId == null) throw new IllegalArgumentException("tenantId は必須");
            if (anchorDate == null) throw new IllegalArgumentException("anchorDate は必須");
        }
    }

    record Report(
            TenantId tenantId,
            LocalDate anchorDate,
            Status status,
            Optional<MerkleAnchor> anchor,
            Optional<HashHex> recomputedRoot,
            long currentRecordCount) {}

    enum Status {
        OK,
        ROOT_MISMATCH,
        ANCHOR_NOT_FOUND,
        RECORD_COUNT_MISMATCH
    }
}
