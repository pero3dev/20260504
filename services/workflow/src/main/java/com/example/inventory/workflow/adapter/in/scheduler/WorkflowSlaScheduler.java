package com.example.inventory.workflow.adapter.in.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.inventory.workflow.application.port.in.ExpireOverdueWorkflowsUseCase;

/**
 * SLA 超過した STARTED インスタンスを定期的に scan して FAILED に強制遷移させる scheduler(ADR-0021 B2)。
 *
 * <p>30 秒間隔で {@link ExpireOverdueWorkflowsUseCase#expireOverdue} を呼び、 SLA 超過したインスタンスを 1 batch
 * ぶん処理する。 24 時間 SLA で 30 秒間隔 = 最遅 30 秒の検知遅延、 通常運用に十分。
 *
 * <p>scheduler は **single replica の HA model** で動かす想定だが、 万が一複数 replica が同時刻に scan しても 各 instance の
 * save が楽観ロックで失敗するだけで重複 FAILED 遷移は起きない。
 *
 * <p>テスト / loadtest profile では `platform.workflow.sla.enabled=false` で無効化可能。
 */
@Component
@ConditionalOnProperty(
        prefix = "platform.workflow.sla",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class WorkflowSlaScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(WorkflowSlaScheduler.class);

    private final ExpireOverdueWorkflowsUseCase useCase;

    public WorkflowSlaScheduler(ExpireOverdueWorkflowsUseCase useCase) {
        this.useCase = useCase;
    }

    @Scheduled(
            fixedDelayString = "${platform.workflow.sla.fixed-delay-ms:30000}",
            initialDelayString = "${platform.workflow.sla.initial-delay-ms:30000}")
    public void scan() {
        try {
            int expired = useCase.expireOverdue();
            if (expired > 0) {
                LOG.debug("SLA scheduler tick: {} instance(s) expired", expired);
            }
        } catch (RuntimeException e) {
            // batch レベルの想定外例外は次回再試行のため swallow + warn。
            LOG.warn("SLA scheduler の実行で想定外例外、 次回再試行: {}", e.toString());
        }
    }
}
