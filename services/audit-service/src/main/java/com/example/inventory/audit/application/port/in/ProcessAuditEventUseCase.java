package com.example.inventory.audit.application.port.in;

import java.time.Instant;

import com.example.inventory.audit.domain.model.AuditOutcome;
import com.example.inventory.commons.tenant.TenantId;

/**
 * 入力ポート: audit.log.v1 から受信した1件をハッシュチェーンに追加する。
 *
 * <p>同じ {@code eventId} で複数回呼ばれた場合は冪等にスキップ(発行側 at-least-once 配信に対応)。
 */
public interface ProcessAuditEventUseCase {

    Result process(Command command);

    record Command(
            TenantId tenantId,
            long eventId,
            String action,
            String targetType,
            String targetId,
            String operatorUserId,
            String operatorTenantId,
            AuditOutcome outcome,
            String errorCode,
            boolean readOnly,
            String payloadJson,
            Instant occurredAt) {}

    enum Result {
        /** 新しくチェーンに追加した。 */
        APPENDED,
        /** 既にチェーン上に同じ event_id があり、冪等にスキップした。 */
        DUPLICATE_SKIPPED
    }
}
