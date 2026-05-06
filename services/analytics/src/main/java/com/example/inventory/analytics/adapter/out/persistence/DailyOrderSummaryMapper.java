package com.example.inventory.analytics.adapter.out.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DailyOrderSummaryMapper {

    List<DailyOrderSummaryRow> findByTenantAndDateRange(
            @Param("tenantId") String tenantId,
            @Param("fromDate") LocalDate fromInclusive,
            @Param("toDate") LocalDate toInclusive,
            @Param("businessContext") String businessContextOrNull);

    int upsertIncrement(
            @Param("tenantId") String tenantId,
            @Param("businessContext") String businessContext,
            @Param("summaryDate") LocalDate summaryDate,
            @Param("currency") String currency,
            @Param("amount") BigDecimal amount,
            @Param("lastEventAt") Instant lastEventAt);
}
