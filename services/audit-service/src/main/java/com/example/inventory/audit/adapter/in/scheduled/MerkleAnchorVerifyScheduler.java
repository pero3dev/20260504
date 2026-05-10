package com.example.inventory.audit.adapter.in.scheduled;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.inventory.audit.application.port.in.VerifyMerkleAnchorUseCase;
import com.example.inventory.audit.application.port.in.VerifyMerkleAnchorUseCase.Report;
import com.example.inventory.audit.application.port.in.VerifyMerkleAnchorUseCase.Status;
import com.example.inventory.audit.config.AnchorProperties;
import com.example.inventory.commons.tenant.TenantId;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * 日次 Merkle anchor 整合性検証スケジューラ(ADR-0008、 A5 follow-up²⁵)。
 *
 * <p>{@link DailyMerkleAnchorScheduler} が anchor を「書く」 のに対し、 本 scheduler は過去 anchor を「再計算して照合する」。
 * 改ざん / 後追い登録 / 後追い削除を 1 日 1 回検出する継続監視層。 monthly compliance audit で人手検証していた工程を自動化する。
 *
 * <p>{@code platform.audit.anchor.verify.enabled=true} かつ {@code tenants} 非空で起動。 既定 cron は UTC
 * 02:00 毎日(計算 scheduler 01:00 の 1 時間後 = 当日分の anchor が確実に書かれた後)。
 *
 * <p>検証結果と log level の対応:
 *
 * <ul>
 *   <li>{@link Status#OK} → INFO
 *   <li>{@link Status#ANCHOR_NOT_FOUND} → WARN(計算 scheduler の取りこぼし or 範囲外日付)
 *   <li>{@link Status#RECORD_COUNT_MISMATCH} → ERROR(後追い登録 / 削除の可能性、 J-SOX 重大事象)
 *   <li>{@link Status#ROOT_MISMATCH} → ERROR(改ざんの可能性、 J-SOX 重大事象)
 * </ul>
 *
 * <p>ERROR ログは Datadog 等のログモニタで page out される設計。 scheduler 自体は continue-on-error で 1 tenant / 1
 * 日付の失敗が他に伝搬しない(VerifyMerkleAnchorService の内部 read-only DB アクセスが落ちた場合のみ catch)。
 *
 * <p>Micrometer metrics(A5 follow-up²⁶):
 *
 * <ul>
 *   <li>{@code audit.anchor.verify.count} — 1 日 1 tenant 1 anchor の verify が完走するたびに +1。 タグ {@code
 *       tenant} と {@code status}(ok / anchor_not_found / record_count_mismatch / root_mismatch)を付与。
 *       Datadog で「 mismatch != 0 が直近 25 時間で発生」 のようなアラートが立てられる。
 *   <li>{@code audit.anchor.verify.exceptions} — verify 中に {@link RuntimeException} を catch するたびに
 *       +1。 タグ {@code tenant} のみ。 DB 切断等の運用障害を J-SOX mismatch と区別して可視化する。
 * </ul>
 */
@Component
@ConditionalOnProperty(
        prefix = "platform.audit.anchor.verify",
        name = "enabled",
        havingValue = "true")
public class MerkleAnchorVerifyScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(MerkleAnchorVerifyScheduler.class);

    static final String METRIC_VERIFY_COUNT = "audit.anchor.verify.count";
    static final String METRIC_VERIFY_EXCEPTIONS = "audit.anchor.verify.exceptions";

    private final VerifyMerkleAnchorUseCase useCase;
    private final AnchorProperties properties;
    private final MeterRegistry meterRegistry;

    public MerkleAnchorVerifyScheduler(
            VerifyMerkleAnchorUseCase useCase,
            AnchorProperties properties,
            MeterRegistry meterRegistry) {
        this.useCase = useCase;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
    }

    @Scheduled(cron = "${platform.audit.anchor.verify.cron:0 0 2 * * *}", zone = "UTC")
    public void verifyRecentAnchorsForAllTenants() {
        if (properties.tenants().isEmpty()) {
            LOG.warn("anchor verify scheduler 起動したが tenants が空のためスキップ");
            return;
        }
        int lookback = properties.verify().lookbackDays();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        int okCount = 0;
        int notFoundCount = 0;
        int mismatchCount = 0;
        for (String tenant : properties.tenants()) {
            TenantId tenantId = new TenantId(tenant);
            for (int dayOffset = 1; dayOffset <= lookback; dayOffset++) {
                LocalDate target = today.minusDays(dayOffset);
                try {
                    Report report =
                            useCase.verify(new VerifyMerkleAnchorUseCase.Command(tenantId, target));
                    switch (report.status()) {
                        case OK -> {
                            okCount++;
                            LOG.info(
                                    "anchor verify OK tenant={} date={} count={}",
                                    tenant,
                                    target,
                                    report.currentRecordCount());
                        }
                        case ANCHOR_NOT_FOUND -> {
                            notFoundCount++;
                            LOG.warn(
                                    "anchor verify 対象 anchor 不在 tenant={} date={}", tenant, target);
                        }
                        case RECORD_COUNT_MISMATCH -> {
                            mismatchCount++;
                            LOG.error(
                                    "anchor verify 件数不整合(J-SOX alert) tenant={} date={}"
                                            + " expected={} actual={}",
                                    tenant,
                                    target,
                                    report.anchor().map(a -> a.recordCount()).orElse(-1L),
                                    report.currentRecordCount());
                        }
                        case ROOT_MISMATCH -> {
                            mismatchCount++;
                            LOG.error(
                                    "anchor verify root 不整合(J-SOX alert) tenant={} date={}"
                                            + " stored={} recomputed={}",
                                    tenant,
                                    target,
                                    report.anchor().map(a -> a.rootHash().value()).orElse("?"),
                                    report.recomputedRoot().map(h -> h.value()).orElse("?"));
                        }
                    }
                    incrementVerifyCount(tenant, report.status());
                } catch (RuntimeException e) {
                    LOG.error(
                            "anchor verify 例外 tenant={} date={}: {}", tenant, target, e.toString());
                    incrementExceptionCount(tenant);
                }
            }
        }
        LOG.info(
                "anchor verify 完了 tenants={} lookbackDays={} ok={} notFound={} mismatch={}",
                properties.tenants().size(),
                lookback,
                okCount,
                notFoundCount,
                mismatchCount);
    }

    private void incrementVerifyCount(String tenant, Status status) {
        Counter.builder(METRIC_VERIFY_COUNT)
                .description("Merkle anchor verify result count by tenant and status")
                .tag("tenant", tenant)
                .tag("status", status.name().toLowerCase(Locale.ROOT))
                .register(meterRegistry)
                .increment();
    }

    private void incrementExceptionCount(String tenant) {
        Counter.builder(METRIC_VERIFY_EXCEPTIONS)
                .description("Merkle anchor verify uncaught exceptions by tenant")
                .tag("tenant", tenant)
                .register(meterRegistry)
                .increment();
    }
}
