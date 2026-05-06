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

/**
 * 日次 Merkle anchor 自動計算スケジューラ(ADR-0008)。
 *
 * <p>{@code platform.audit.anchor.enabled=true} かつ {@code tenants} が非空の場合のみ起動する。 既定 cron は UTC
 * 01:00 毎日 — その時点で「前日(UTC)」分の anchor を計算する。 既存 anchor が同 (tenant, date) で存在すれば計算スキップ(冪等)。
 *
 * <p>失敗(対象テナントが多数あり一部失敗)は次回再試行で吸収する設計。各テナントの計算は独立 TX なので、 1 テナントのエラーが他テナントに影響しない。
 */
@Component
@ConditionalOnProperty(prefix = "platform.audit.anchor", name = "enabled", havingValue = "true")
public class DailyMerkleAnchorScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(DailyMerkleAnchorScheduler.class);

    private final ComputeDailyMerkleAnchorUseCase useCase;
    private final AnchorProperties properties;

    public DailyMerkleAnchorScheduler(
            ComputeDailyMerkleAnchorUseCase useCase, AnchorProperties properties) {
        this.useCase = useCase;
        this.properties = properties;
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
                } else {
                    LOG.info(
                            "anchor 完了 tenant={} date={} count={}",
                            tenant,
                            yesterday,
                            result.anchor().recordCount());
                }
            } catch (RuntimeException e) {
                LOG.error("anchor 計算失敗 tenant={} date={}: {}", tenant, yesterday, e.toString());
            }
        }
    }
}
