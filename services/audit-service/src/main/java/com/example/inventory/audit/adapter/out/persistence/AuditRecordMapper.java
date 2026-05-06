package com.example.inventory.audit.adapter.out.persistence;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AuditRecordMapper {

    AuditRecordRow findLatest(@Param("tenantId") String tenantId);

    int existsByEventId(@Param("eventId") long eventId);

    int insert(@Param("row") AuditRecordRow row);

    /** Postgres advisory lock(transaction-scoped)。tenant_id のハッシュをキーにロック。 */
    void acquireTenantLock(@Param("tenantId") String tenantId);

    /** チェーン整合性検証用: 同テナントの全レコードを sequence 昇順で返す。 */
    List<AuditRecordRow> findAllOrderedBySequence(@Param("tenantId") String tenantId);

    /** Merkle anchor 用: occurred_at 範囲 [from, to) の同テナントレコードを sequence 昇順で返す。 */
    List<AuditRecordRow> findByOccurredRange(
            @Param("tenantId") String tenantId,
            @Param("fromInclusive") Instant fromInclusive,
            @Param("toExclusive") Instant toExclusive);
}
