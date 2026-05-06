package com.example.inventory.analytics.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.dao.DuplicateKeyException;

import com.example.inventory.analytics.application.port.in.IngestOrderPlacedUseCase;
import com.example.inventory.analytics.application.port.in.IngestOrderPlacedUseCase.Result;
import com.example.inventory.analytics.application.port.out.DailyOrderSummaryRepository;
import com.example.inventory.analytics.application.port.out.ProcessedEventRepository;
import com.example.inventory.analytics.domain.model.BusinessContext;
import com.example.inventory.commons.tenant.TenantId;

class IngestOrderPlacedServiceTest {

    private static final TenantId TENANT = new TenantId("tenant-1");
    private static final Instant OCCURRED_AT = Instant.parse("2026-05-06T13:30:00Z");

    private ProcessedEventRepository processedRepo;
    private DailyOrderSummaryRepository summaryRepo;
    private IngestOrderPlacedService service;

    @BeforeEach
    void setUp() {
        processedRepo = Mockito.mock(ProcessedEventRepository.class);
        summaryRepo = Mockito.mock(DailyOrderSummaryRepository.class);
        service = new IngestOrderPlacedService(processedRepo, summaryRepo);
    }

    @Test
    void 新規イベントは_processed_event_に_INSERT_され_集計_UPSERT_を呼ぶ() {
        Result result =
                service.ingest(
                        new IngestOrderPlacedUseCase.Command(
                                999L,
                                TENANT,
                                BusinessContext.RETAIL,
                                "JPY",
                                new BigDecimal("1500"),
                                OCCURRED_AT));

        assertThat(result).isEqualTo(Result.AGGREGATED);
        verify(processedRepo).markProcessed(eq(999L), eq(TENANT), any());
        verify(summaryRepo)
                .incrementOrder(
                        eq(TENANT),
                        eq(BusinessContext.RETAIL),
                        eq(LocalDate.of(2026, 5, 6)),
                        eq("JPY"),
                        eq(new BigDecimal("1500")),
                        eq(OCCURRED_AT));
    }

    @Test
    void 既処理イベントは_DuplicateKeyException_を吸収して_DUPLICATE_SKIPPED_を返す() {
        Mockito.doThrow(new DuplicateKeyException("dup"))
                .when(processedRepo)
                .markProcessed(any(Long.class), any(TenantId.class), any());

        Result result =
                service.ingest(
                        new IngestOrderPlacedUseCase.Command(
                                999L,
                                TENANT,
                                BusinessContext.WHOLESALE,
                                "JPY",
                                new BigDecimal("1500"),
                                OCCURRED_AT));

        assertThat(result).isEqualTo(Result.DUPLICATE_SKIPPED);
        verify(summaryRepo, never()).incrementOrder(any(), any(), any(), any(), any(), any());
    }

    @Test
    void 集計の_summary_date_は_occurred_at_の_UTC_日付に揃える() {
        // JST 2026-05-07 04:00 = UTC 2026-05-06 19:00 → summary_date は 2026-05-06
        Instant lateUtc = Instant.parse("2026-05-06T19:00:00Z");

        service.ingest(
                new IngestOrderPlacedUseCase.Command(
                        1L,
                        TENANT,
                        BusinessContext.WHOLESALE,
                        "JPY",
                        new BigDecimal("100"),
                        lateUtc));

        verify(summaryRepo)
                .incrementOrder(
                        eq(TENANT),
                        eq(BusinessContext.WHOLESALE),
                        eq(LocalDate.of(2026, 5, 6)),
                        eq("JPY"),
                        eq(new BigDecimal("100")),
                        eq(lateUtc));
    }
}
