package com.example.inventory.analytics.application.port.in;

import java.math.BigDecimal;
import java.time.Instant;

import com.example.inventory.analytics.domain.model.BusinessContext;
import com.example.inventory.commons.tenant.TenantId;

/**
 * 業態系の注文確定イベント({@code retail.order.placed.v1} / {@code wholesale.order.placed.v1})を 取り込んで
 * daily_order_summary に集計するユースケース。
 *
 * <p>{@code eventId} で冪等性確認。既処理ならスキップ。新規なら UPSERT で集計に加算する。
 */
public interface IngestOrderPlacedUseCase {

    Result ingest(Command command);

    record Command(
            long eventId,
            TenantId tenantId,
            BusinessContext businessContext,
            String currency,
            BigDecimal totalAmount,
            Instant occurredAt) {

        public Command {
            if (tenantId == null) throw new IllegalArgumentException("tenantId は必須");
            if (businessContext == null) throw new IllegalArgumentException("businessContext は必須");
            if (currency == null || currency.length() != 3)
                throw new IllegalArgumentException("currency は 3 文字 ISO 4217");
            if (totalAmount == null || totalAmount.signum() < 0)
                throw new IllegalArgumentException("totalAmount は非負必須");
            if (occurredAt == null) throw new IllegalArgumentException("occurredAt は必須");
        }
    }

    enum Result {
        AGGREGATED,
        DUPLICATE_SKIPPED
    }
}
