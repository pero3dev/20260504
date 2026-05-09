package com.example.inventory.analytics.application.usecase;

import java.time.LocalDate;
import java.time.ZoneOffset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.inventory.analytics.application.port.in.IngestOrderPlacedUseCase;
import com.example.inventory.analytics.application.port.out.DailyOrderSummaryRepository;
import com.example.inventory.analytics.application.port.out.ProcessedEventRepository;
import com.example.inventory.commons.audit.AuditExempt;

/**
 * 注文確定イベント取り込みサービス。
 *
 * <p>処理:
 *
 * <ol>
 *   <li>processed_event に event_id INSERT(冪等性キー)
 *   <li>summary_date = occurred_at の UTC 日付
 *   <li>daily_order_summary に UPSERT で加算(order_count += 1, total_amount += amount)
 * </ol>
 *
 * <p>{@code DuplicateKeyException} = 既処理イベント。冪等にスキップ。
 */
@Service
public class IngestOrderPlacedService implements IngestOrderPlacedUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(IngestOrderPlacedService.class);

    private static final String TOPIC_HINT = "order.placed";

    private final ProcessedEventRepository processedRepo;
    private final DailyOrderSummaryRepository summaryRepo;

    public IngestOrderPlacedService(
            ProcessedEventRepository processedRepo, DailyOrderSummaryRepository summaryRepo) {
        this.processedRepo = processedRepo;
        this.summaryRepo = summaryRepo;
    }

    @Override
    @Transactional
    @AuditExempt(
            reason =
                    "Kafka projection。 元 order placement event は発生源 service (retail-ec / wholesale 等) で"
                            + " 既に audit 済のため、 集計側でも打つと一操作 = 複数監査記録の二重カウント")
    public Result ingest(Command command) {
        try {
            processedRepo.markProcessed(command.eventId(), command.tenantId(), TOPIC_HINT);
        } catch (DuplicateKeyException e) {
            LOG.debug("既処理イベントをスキップ event_id={}", command.eventId());
            return Result.DUPLICATE_SKIPPED;
        }

        LocalDate summaryDate = command.occurredAt().atZone(ZoneOffset.UTC).toLocalDate();
        summaryRepo.incrementOrder(
                command.tenantId(),
                command.businessContext(),
                summaryDate,
                command.currency(),
                command.totalAmount(),
                command.occurredAt());

        LOG.debug(
                "analytics 集計 event_id={} tenant={} ctx={} date={} amount={}",
                command.eventId(),
                command.tenantId().value(),
                command.businessContext(),
                summaryDate,
                command.totalAmount());
        return Result.AGGREGATED;
    }
}
