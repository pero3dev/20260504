package com.example.inventory.analytics.application.port.in;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.example.inventory.analytics.domain.model.BusinessContext;
import com.example.inventory.analytics.domain.model.DailyOrderSummary;
import com.example.inventory.commons.tenant.TenantId;

/** 期間指定でテナントの daily_order_summary を取得するユースケース(検索 API)。 */
public interface GetDailyOrderSummariesUseCase {

    List<DailyOrderSummary> get(Query query);

    record Query(
            TenantId tenantId,
            LocalDate fromDate,
            LocalDate toDate,
            Optional<BusinessContext> businessContext) {

        public Query {
            if (tenantId == null) throw new IllegalArgumentException("tenantId は必須");
            if (fromDate == null) throw new IllegalArgumentException("fromDate は必須");
            if (toDate == null) throw new IllegalArgumentException("toDate は必須");
            if (toDate.isBefore(fromDate))
                throw new IllegalArgumentException("toDate は fromDate 以降");
            if (businessContext == null) businessContext = Optional.empty();
        }
    }
}
