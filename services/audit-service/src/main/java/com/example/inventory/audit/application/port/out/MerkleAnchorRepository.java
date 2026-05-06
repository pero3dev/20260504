package com.example.inventory.audit.application.port.out;

import java.time.LocalDate;
import java.util.Optional;

import com.example.inventory.audit.domain.model.MerkleAnchor;
import com.example.inventory.commons.tenant.TenantId;

/**
 * Merkle anchor の永続化ポート。
 *
 * <p>WORM 保護対象: append のみ許可、UPDATE/DELETE は DB トリガで拒否される(V2 マイグレーション)。
 */
public interface MerkleAnchorRepository {

    Optional<MerkleAnchor> find(TenantId tenantId, LocalDate anchorDate);

    /**
     * Anchor を append する。同 (tenant, date) が既存なら {@link
     * org.springframework.dao.DuplicateKeyException} を投げる(再計算を試みた呼び出し側がハンドリング)。
     */
    void append(MerkleAnchor anchor);
}
