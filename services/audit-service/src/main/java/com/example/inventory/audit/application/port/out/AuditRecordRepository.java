package com.example.inventory.audit.application.port.out;

import java.util.Optional;

import com.example.inventory.audit.domain.model.AuditRecord;
import com.example.inventory.commons.tenant.TenantId;

/**
 * 監査レコード永続化ポート。
 *
 * <p>実装上の必須要件:
 *
 * <ul>
 *   <li>{@link #findLatest(TenantId)} は同一トランザクション内の advisory lock を前提とし、 並行 consumer 同士が同じテナントへ
 *       append しない様にすること
 *   <li>{@code event_id} の UNIQUE 制約により重複 INSERT は DB が拒否する。 実装側で {@code DuplicateKeyException} を
 *       Optional.empty() 等にラップしないこと (上位ユースケースが冪等判定する)
 * </ul>
 */
public interface AuditRecordRepository {

    /** テナントごとのチェーン上、最新(=最大 sequence)のレコードを返す。 戻り値の存在で「初回 append か」を判定する。 */
    Optional<AuditRecord> findLatest(TenantId tenantId);

    /** 同一テナント内で event_id がすでに存在するか。冪等性チェック用。 */
    boolean existsByEventId(long eventId);

    /** 新しいレコードを append する。{@code event_id} 重複時は {@code DuplicateKeyException} を伝搬させる。 */
    void append(AuditRecord record);

    /** Postgres advisory lock(transaction-scoped)。同テナントへの並行 append を直列化する。 */
    void acquireTenantLock(TenantId tenantId);
}
