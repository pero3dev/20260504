package com.example.inventory.audit.adapter.out.persistence;

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
}
