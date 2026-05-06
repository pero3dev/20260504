package com.example.inventory.analytics.application.port.out;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.example.inventory.analytics.domain.model.BusinessContext;
import com.example.inventory.analytics.domain.model.DailyOrderSummary;
import com.example.inventory.commons.tenant.TenantId;

public interface DailyOrderSummaryRepository {

    /** 期間範囲のサマリを返す(検索用)。 */
    List<DailyOrderSummary> findByTenantAndDateRange(
            TenantId tenantId,
            LocalDate fromInclusive,
            LocalDate toInclusive,
            Optional<BusinessContext> businessContext);

    /**
     * UPSERT による加算。bucket 不在時は INSERT、既存時は order_count += 1, total_amount += amount。 同一 bucket への並行
     * UPSERT は Postgres の {@code INSERT ... ON CONFLICT DO UPDATE} で直列化。
     */
    void incrementOrder(
            TenantId tenantId,
            BusinessContext businessContext,
            LocalDate summaryDate,
            String currency,
            java.math.BigDecimal amount,
            java.time.Instant lastEventAt);
}
