package com.example.inventory.analytics.application.usecase;

import java.util.List;

import org.springframework.stereotype.Service;

import com.example.inventory.analytics.application.port.in.GetDailyOrderSummariesUseCase;
import com.example.inventory.analytics.application.port.out.DailyOrderSummaryRepository;
import com.example.inventory.analytics.domain.model.DailyOrderSummary;

@Service
public class GetDailyOrderSummariesService implements GetDailyOrderSummariesUseCase {

    private final DailyOrderSummaryRepository repository;

    public GetDailyOrderSummariesService(DailyOrderSummaryRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<DailyOrderSummary> get(Query query) {
        return repository.findByTenantAndDateRange(
                query.tenantId(), query.fromDate(), query.toDate(), query.businessContext());
    }
}
