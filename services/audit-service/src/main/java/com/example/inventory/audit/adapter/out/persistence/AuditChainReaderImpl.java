package com.example.inventory.audit.adapter.out.persistence;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.example.inventory.audit.application.port.out.AuditChainReader;
import com.example.inventory.audit.domain.model.AuditOutcome;
import com.example.inventory.audit.domain.model.AuditRecord;
import com.example.inventory.audit.domain.model.HashHex;
import com.example.inventory.commons.tenant.TenantId;

@Repository
public class AuditChainReaderImpl implements AuditChainReader {

    private final AuditRecordMapper mapper;

    public AuditChainReaderImpl(AuditRecordMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<AuditRecord> findAllOrderedBySequence(TenantId tenantId) {
        return mapper.findAllOrderedBySequence(tenantId.value()).stream()
                .map(AuditChainReaderImpl::toDomain)
                .toList();
    }

    @Override
    public List<AuditRecord> findByOccurredRange(
            TenantId tenantId, Instant fromInclusive, Instant toExclusive) {
        return mapper.findByOccurredRange(tenantId.value(), fromInclusive, toExclusive).stream()
                .map(AuditChainReaderImpl::toDomain)
                .toList();
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
}
