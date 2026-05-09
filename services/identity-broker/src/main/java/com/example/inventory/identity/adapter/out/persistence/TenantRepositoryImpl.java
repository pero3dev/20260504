package com.example.inventory.identity.adapter.out.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.example.inventory.commons.tenant.TenantId;
import com.example.inventory.identity.application.port.out.TenantRepository;
import com.example.inventory.identity.domain.model.Tenant;
import com.example.inventory.identity.domain.model.TenantStatus;

@Repository
public class TenantRepositoryImpl implements TenantRepository {

    private final TenantMapper mapper;

    public TenantRepositoryImpl(TenantMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void append(Tenant tenant) {
        mapper.insert(toRow(tenant));
    }

    @Override
    public void update(Tenant tenant) {
        mapper.updateStatus(toRow(tenant));
    }

    @Override
    public Optional<Tenant> findById(TenantId tenantId) {
        TenantRow row = mapper.findById(tenantId.value());
        return row == null ? Optional.empty() : Optional.of(toDomain(row));
    }

    @Override
    public List<Tenant> findAll() {
        return mapper.findAll().stream().map(TenantRepositoryImpl::toDomain).toList();
    }

    private static TenantRow toRow(Tenant tenant) {
        return new TenantRow(
                tenant.tenantId().value(),
                tenant.displayName(),
                tenant.status().name(),
                tenant.createdAt(),
                tenant.deactivatedAt(),
                tenant.locale());
    }

    private static Tenant toDomain(TenantRow row) {
        return Tenant.restore(
                new TenantId(row.tenantId()),
                row.displayName(),
                TenantStatus.valueOf(row.status()),
                row.createdAt(),
                row.deactivatedAt(),
                row.locale() != null ? row.locale() : Tenant.DEFAULT_LOCALE);
    }
}
