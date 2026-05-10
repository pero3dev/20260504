package com.example.inventory.audit.adapter.in.scheduled;

import java.time.LocalDate;
import java.time.ZoneOffset;

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
 */
@Component
@ConditionalOnProperty(
        prefix = "platform.audit.anchor.verify",
        name = "enabled",
        havingValue = "true")
public class MerkleAnchorVerifyScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(MerkleAnchorVerifyScheduler.class);

    private final VerifyMerkleAnchorUseCase useCase;
    private final AnchorProperties properties;

    public MerkleAnchorVerifyScheduler(
            VerifyMerkleAnchorUseCase useCase, AnchorProperties properties) {
        this.useCase = useCase;
        this.properties = properties;
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
                } catch (RuntimeException e) {
                    LOG.error(
                            "anchor verify 例外 tenant={} date={}: {}", tenant, target, e.toString());
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
}
