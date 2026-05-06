package com.example.inventory.audit.adapter.out.persistence;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.example.inventory.audit.application.port.out.MerkleAnchorRepository;
import com.example.inventory.audit.domain.model.HashHex;
import com.example.inventory.audit.domain.model.MerkleAnchor;
import com.example.inventory.commons.tenant.TenantId;

@Repository
public class MerkleAnchorRepositoryImpl implements MerkleAnchorRepository {

    private final MerkleAnchorMapper mapper;

    public MerkleAnchorRepositoryImpl(MerkleAnchorMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<MerkleAnchor> find(TenantId tenantId, LocalDate anchorDate) {
        MerkleAnchorRow row = mapper.find(tenantId.value(), anchorDate);
        return row == null ? Optional.empty() : Optional.of(toDomain(row));
    }

    @Override
    public void append(MerkleAnchor anchor) {
        mapper.insert(toRow(anchor));
    }

    private static MerkleAnchor toDomain(MerkleAnchorRow row) {
        return new MerkleAnchor(
                new TenantId(row.tenantId()),
                row.anchorDate(),
                new HashHex(row.rootHash()),
                row.recordCount(),
                row.firstSequence(),
                row.lastSequence(),
                row.computedAt());
    }

    private static MerkleAnchorRow toRow(MerkleAnchor a) {
        return new MerkleAnchorRow(
                a.tenantId().value(),
                a.anchorDate(),
                a.rootHash().value(),
                a.recordCount(),
                a.firstSequence(),
                a.lastSequence(),
                a.computedAt());
    }
}
