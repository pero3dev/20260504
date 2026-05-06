package com.example.inventory.analytics.adapter.in.rest;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.example.inventory.analytics.adapter.in.rest.api.DailyOrdersApi;
import com.example.inventory.analytics.adapter.in.rest.api.model.DailyOrderSummaryResponse;
import com.example.inventory.analytics.application.port.in.GetDailyOrderSummariesUseCase;
import com.example.inventory.analytics.domain.model.BusinessContext;
import com.example.inventory.analytics.domain.model.DailyOrderSummary;
import com.example.inventory.commons.tenant.TenantId;

/** Daily order summaries の検索 REST。OpenAPI 生成 {@link DailyOrdersApi} を実装(ADR-0006)。 */
@RestController
public class AnalyticsController implements DailyOrdersApi {

    private final GetDailyOrderSummariesUseCase getSummaries;

    public AnalyticsController(GetDailyOrderSummariesUseCase getSummaries) {
        this.getSummaries = getSummaries;
    }

    @Override
    public ResponseEntity<List<DailyOrderSummaryResponse>> getDailyOrderSummaries(
            String tenant, LocalDate from, LocalDate to, String businessContext) {
        Optional<BusinessContext> ctx =
                businessContext == null
                        ? Optional.empty()
                        : Optional.of(BusinessContext.valueOf(businessContext));
        List<DailyOrderSummary> result =
                getSummaries.get(
                        new GetDailyOrderSummariesUseCase.Query(
                                new TenantId(tenant), from, to, ctx));
        return ResponseEntity.ok(result.stream().map(AnalyticsController::toResponse).toList());
    }

    private static DailyOrderSummaryResponse toResponse(DailyOrderSummary s) {
        DailyOrderSummaryResponse r = new DailyOrderSummaryResponse();
        r.setTenantId(s.tenantId().value());
        r.setBusinessContext(
                DailyOrderSummaryResponse.BusinessContextEnum.valueOf(s.businessContext().name()));
        r.setSummaryDate(s.summaryDate());
        r.setCurrency(s.currency());
        r.setOrderCount(s.orderCount());
        r.setTotalAmount(s.totalAmount().doubleValue());
        r.setLastEventAt(OffsetDateTime.ofInstant(s.lastEventAt(), ZoneOffset.UTC));
        return r;
    }
}
