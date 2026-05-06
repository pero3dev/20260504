package com.example.inventory.analytics.adapter.out.persistence;

import org.springframework.stereotype.Repository;

import com.example.inventory.analytics.application.port.out.ProcessedEventRepository;
import com.example.inventory.commons.tenant.TenantId;

@Repository
public class ProcessedEventRepositoryImpl implements ProcessedEventRepository {

    private final ProcessedEventMapper mapper;

    public ProcessedEventRepositoryImpl(ProcessedEventMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public boolean exists(long eventId) {
        return mapper.existsByEventId(eventId) > 0;
    }

    @Override
    public void markProcessed(long eventId, TenantId tenantId, String topic) {
        mapper.insert(eventId, tenantId.value(), topic);
    }
}
