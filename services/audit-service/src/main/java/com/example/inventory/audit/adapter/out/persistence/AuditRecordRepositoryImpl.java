package com.example.inventory.audit.adapter.out.persistence;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.example.inventory.audit.application.port.out.AuditRecordRepository;
import com.example.inventory.audit.domain.model.AuditOutcome;
import com.example.inventory.audit.domain.model.AuditRecord;
import com.example.inventory.audit.domain.model.HashHex;
import com.example.inventory.commons.tenant.TenantId;

@Repository
public class AuditRecordRepositoryImpl implements AuditRecordRepository {

    private final AuditRecordMapper mapper;

    public AuditRecordRepositoryImpl(AuditRecordMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<AuditRecord> findLatest(TenantId tenantId) {
        AuditRecordRow row = mapper.findLatest(tenantId.value());
        return row == null ? Optional.empty() : Optional.of(toDomain(row));
    }

    @Override
    public boolean existsByEventId(long eventId) {
        return mapper.existsByEventId(eventId) > 0;
    }

    @Override
    public void append(AuditRecord record) {
        mapper.insert(toRow(record));
    }

    @Override
    public void acquireTenantLock(TenantId tenantId) {
        mapper.acquireTenantLock(tenantId.value());
    }

    private static AuditRecord toDomain(AuditRecordRow row) {
        return new AuditRecord(
                new TenantId(row.tenantId()),
                row.sequence(),
                row.eventId(),
                row.action(),
                row.targetType(),
                row.targetId(),
                row.operatorUserId(),
                row.operatorTenantId(),
                AuditOutcome.valueOf(row.outcome()),
                row.errorCode(),
                row.readOnly(),
                row.payloadJson(),
                row.occurredAt(),
                new HashHex(row.prevHash()),
                new HashHex(row.hash()));
    }

    private static AuditRecordRow toRow(AuditRecord r) {
        return new AuditRecordRow(
                r.tenantId().value(),
                r.sequence(),
                r.eventId(),
                r.action(),
                r.targetType(),
                r.targetId(),
                r.operatorUserId(),
                r.operatorTenantId(),
                r.outcome().name(),
                r.errorCode(),
                r.readOnly(),
                r.payloadJson(),
                r.occurredAt(),
                r.prevHash().value(),
                r.hash().value());
    }
}
