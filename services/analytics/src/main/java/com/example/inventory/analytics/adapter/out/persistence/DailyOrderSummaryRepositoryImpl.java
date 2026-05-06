package com.example.inventory.analytics.adapter.out.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.example.inventory.analytics.application.port.out.DailyOrderSummaryRepository;
import com.example.inventory.analytics.domain.model.BusinessContext;
import com.example.inventory.analytics.domain.model.DailyOrderSummary;
import com.example.inventory.commons.tenant.TenantId;

@Repository
public class DailyOrderSummaryRepositoryImpl implements DailyOrderSummaryRepository {

    private final DailyOrderSummaryMapper mapper;

    public DailyOrderSummaryRepositoryImpl(DailyOrderSummaryMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<DailyOrderSummary> findByTenantAndDateRange(
            TenantId tenantId,
            LocalDate fromInclusive,
            LocalDate toInclusive,
            Optional<BusinessContext> businessContext) {
        String ctxValue = businessContext.map(BusinessContext::dbValue).orElse(null);
        return mapper
                .findByTenantAndDateRange(tenantId.value(), fromInclusive, toInclusive, ctxValue)
                .stream()
                .map(DailyOrderSummaryRepositoryImpl::toDomain)
                .toList();
    }

    @Override
    public void incrementOrder(
            TenantId tenantId,
            BusinessContext businessContext,
            LocalDate summaryDate,
            String currency,
            BigDecimal amount,
            Instant lastEventAt) {
        mapper.upsertIncrement(
                tenantId.value(),
                businessContext.dbValue(),
                summaryDate,
                currency,
                amount,
                lastEventAt);
    }

    private static DailyOrderSummary toDomain(DailyOrderSummaryRow row) {
        return new DailyOrderSummary(
                new TenantId(row.tenantId()),
                BusinessContext.fromDbValue(row.businessContext()),
                row.summaryDate(),
                row.currency(),
                row.orderCount(),
                row.totalAmount(),
                row.lastEventAt());
    }
}
