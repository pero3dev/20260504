package com.example.inventory.audit.application.port.out;

import java.time.Instant;
import java.util.List;

import com.example.inventory.audit.domain.model.AuditRecord;
import com.example.inventory.commons.tenant.TenantId;

/**
 * チェーン整合性検証用の読み取り専用ポート。
 *
 * <p>append 系の {@link AuditRecordRepository} とは責務分離。読取しか必要としない用途で 書込メソッドを誤呼出ししないため。
 *
 * <p>TODO(MVP): 1テナントの全レコードを一括ロードする。本番運用ではページング必須 (1テナント数百万行のシナリオ)。{@code findRange(tenantId,
 * fromSeq, limit)} に進化させる。
 */
public interface AuditChainReader {

    /** 指定テナントの監査レコードを sequence 昇順で返す。空テナントは空リスト。 */
    List<AuditRecord> findAllOrderedBySequence(TenantId tenantId);

    /**
     * 指定テナント × 期間({@code fromInclusive ≤ occurred_at < toExclusive})のレコードを sequence 昇順で返す。 Merkle
     * anchor 計算用。空期間は空リスト。
     */
    List<AuditRecord> findByOccurredRange(
            TenantId tenantId, Instant fromInclusive, Instant toExclusive);
}
