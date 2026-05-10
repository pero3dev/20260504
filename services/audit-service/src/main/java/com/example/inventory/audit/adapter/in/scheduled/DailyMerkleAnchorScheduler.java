package com.example.inventory.audit.adapter.in.scheduled;

import java.time.LocalDate;
import java.time.ZoneOffset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.inventory.audit.application.port.in.ComputeDailyMerkleAnchorUseCase;
import com.example.inventory.audit.config.AnchorProperties;
import com.example.inventory.commons.tenant.TenantId;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * 日次 Merkle anchor 自動計算スケジューラ(ADR-0008)。
 *
 * <p>{@code platform.audit.anchor.enabled=true} かつ {@code tenants} が非空の場合のみ起動する。 既定 cron は UTC
 * 01:00 毎日 — その時点で「前日(UTC)」分の anchor を計算する。 既存 anchor が同 (tenant, date) で存在すれば計算スキップ(冪等)。
 *
 * <p>失敗(対象テナントが多数あり一部失敗)は次回再試行で吸収する設計。各テナントの計算は独立 TX なので、 1 テナントのエラーが他テナントに影響しない。
 *
 * <p>Micrometer metrics(A5 follow-up²⁷):
 *
 * <ul>
 *   <li>{@code audit.anchor.compute.count} — 1 tenant の compute が完走するたびに +1。 タグ {@code tenant} と
 *       {@code status}({@code created} = 新規生成 / {@code already_anchored} = 既存 anchor 検出で no-op)。
 *       Datadog で「直近 25 時間の `created` 合計が tenant 数と一致しない」 のような sweep 未走行アラートに使う。
 *   <li>{@code audit.anchor.compute.exceptions} — compute 中に {@link RuntimeException} を catch するたびに
 *       +1。 タグ {@code tenant} のみ。 単発エラーは次 sweep で再試行されるが、 連続で立つと Aurora 障害等の継続障害として page out。
 * </ul>
 *
 * <p>verify scheduler ({@link MerkleAnchorVerifyScheduler}) と対をなし、 write/read 双方向の sweep を Datadog
 * で可視化する。
 */
@Component
@ConditionalOnProperty(prefix = "platform.audit.anchor", name = "enabled", havingValue = "true")
public class DailyMerkleAnchorScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(DailyMerkleAnchorScheduler.class);

    static final String METRIC_COMPUTE_COUNT = "audit.anchor.compute.count";
    static final String METRIC_COMPUTE_EXCEPTIONS = "audit.anchor.compute.exceptions";

    private final ComputeDailyMerkleAnchorUseCase useCase;
    private final AnchorProperties properties;
    private final MeterRegistry meterRegistry;

    public DailyMerkleAnchorScheduler(
            ComputeDailyMerkleAnchorUseCase useCase,
            AnchorProperties properties,
            MeterRegistry meterRegistry) {
        this.useCase = useCase;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
    }

    @Scheduled(cron = "${platform.audit.anchor.cron:0 0 1 * * *}", zone = "UTC")
    public void anchorYesterdayForAllTenants() {
        if (properties.tenants().isEmpty()) {
            LOG.warn("anchor scheduler 起動したが tenants が空のためスキップ");
            return;
        }
        LocalDate yesterday = LocalDate.now(ZoneOffset.UTC).minusDays(1);
        for (String tenant : properties.tenants()) {
            try {
                ComputeDailyMerkleAnchorUseCase.Result result =
                        useCase.compute(
                                new ComputeDailyMerkleAnchorUseCase.Command(
                                        new TenantId(tenant), yesterday));
                if (result.alreadyAnchored()) {
                    LOG.debug("anchor 既存 tenant={} date={}", tenant, yesterday);
                    incrementComputeCount(tenant, "already_anchored");
                } else {
                    LOG.info(
                            "anchor 完了 tenant={} date={} count={}",
                            tenant,
                            yesterday,
                            result.anchor().recordCount());
                    incrementComputeCount(tenant, "created");
                }
            } catch (RuntimeException e) {
                LOG.error("anchor 計算失敗 tenant={} date={}: {}", tenant, yesterday, e.toString());
                incrementExceptionCount(tenant);
            }
        }
    }

    private void incrementComputeCount(String tenant, String status) {
        Counter.builder(METRIC_COMPUTE_COUNT)
                .description("Merkle anchor compute result count by tenant and status")
                .tag("tenant", tenant)
                .tag("status", status)
                .register(meterRegistry)
                .increment();
    }

    private void incrementExceptionCount(String tenant) {
        Counter.builder(METRIC_COMPUTE_EXCEPTIONS)
                .description("Merkle anchor compute uncaught exceptions by tenant")
                .tag("tenant", tenant)
                .register(meterRegistry)
                .increment();
    }
}
