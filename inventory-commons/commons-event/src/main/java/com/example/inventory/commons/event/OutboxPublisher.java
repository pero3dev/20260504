package com.example.inventory.commons.event;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.inventory.commons.tenant.TenantContext;
import com.example.inventory.commons.tenant.TenantId;

/**
 * Transactional Outbox(ADR-0009)のドレイナ。
 *
 * <p>サービスのプロセス内で定期起動され、自サービスの outbox テーブルから未発行行を取り出して Kafka に発行する。テナントスキーマ単位に走査するため、{@link
 * TenantDirectory} から 対象テナントを得て {@link TenantContext} を切替えながら処理する。
 *
 * <p><b>並行制御:</b> 複数 Pod が同時に走査するため、 {@link OutboxRepository#pickUnpublished(int)} の実装は {@code
 * SELECT ... FOR UPDATE SKIP LOCKED} で行ロックすること(本クラスは 1テナント1ループあたり {@link Transactional} 内で動作する)。
 *
 * <p><b>失敗時の挙動:</b> Kafka 発行失敗時は published を更新しない。次回周期で自動再試行。 コンシューマ側が冪等であれば(eventId
 * 単位)、再発行による重複は安全。
 *
 * <p><b>タイムアウト:</b> 1イベントあたりの ack 待機を {@code platform.outbox.send-timeout}
 * (既定5秒)で打ち切る。タイムアウトしたイベントは未発行のまま次回周期で再試行。
 */
public class OutboxPublisher {

    private static final Logger LOG = LoggerFactory.getLogger(OutboxPublisher.class);

    private static final Duration SEND_TIMEOUT = Duration.ofSeconds(5);

    private final TenantDirectory tenantDirectory;
    private final OutboxRepository outboxRepository;
    private final OutboxKafkaSender sender;
    private final OutboxProperties properties;
    private final TransactionTemplate transactionTemplate;

    public OutboxPublisher(
            TenantDirectory tenantDirectory,
            OutboxRepository outboxRepository,
            OutboxKafkaSender sender,
            OutboxProperties properties,
            PlatformTransactionManager transactionManager) {
        this.tenantDirectory = tenantDirectory;
        this.outboxRepository = outboxRepository;
        this.sender = sender;
        this.properties = properties;
        // 同一 Bean 内で呼ぶと @Transactional の self-invocation 問題で TX が始まらず、
        // commons-tenant の SET LOCAL search_path が無効になる。TransactionTemplate を直接使う。
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(
                org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * 走査周期。Spring の {@code fixedDelayString} は ISO-8601 Duration 表記 (例 {@code PT1S})を直接解釈する。YAML 上は
     * relaxed binding により {@code 1s} と書ける。
     */
    @Scheduled(fixedDelayString = "${platform.outbox.poll-interval:PT1S}")
    public void drain() {
        for (TenantId tenant : tenantDirectory.activeTenants()) {
            try {
                drainTenant(tenant);
            } catch (RuntimeException e) {
                // 1テナントの失敗が他テナントの処理を止めないようにする。
                LOG.warn("テナント {} の outbox ドレインで例外発生: {}", tenant.value(), e.toString());
            }
        }
    }

    /**
     * 1テナント1周期分を1トランザクションで処理する。{@link OutboxRepository#pickUnpublished(int)} の {@code FOR UPDATE
     * SKIP LOCKED} と同一トランザクションでなければ行ロックの効果が無い点に注意。
     *
     * <p>{@link TransactionTemplate} を直接使うのは、{@link Scheduled} の {@code drain()} から このメソッドを呼ぶと
     * self-invocation で {@code @Transactional} が効かないため。 TX が始まらないと commons-tenant の {@code SET
     * LOCAL search_path} も無効化され、 default schema(public)で {@code outbox} を探して relation not found
     * になる。
     */
    public void drainTenant(TenantId tenant) {
        TenantContext.set(tenant);
        try {
            transactionTemplate.executeWithoutResult(status -> drainBatch(tenant));
        } finally {
            TenantContext.clear();
        }
    }

    private void drainBatch(TenantId tenant) {
        List<OutboxRecord> batch = outboxRepository.pickUnpublished(properties.batchSize());
        if (batch.isEmpty()) {
            return;
        }
        int published = 0;
        for (OutboxRecord rec : batch) {
            if (publishOne(rec)) {
                outboxRepository.markPublished(rec.eventId());
                published++;
            }
        }
        if (published > 0) {
            LOG.debug("テナント {} で {} 件の outbox イベントを発行", tenant.value(), published);
        }
    }

    private boolean publishOne(OutboxRecord rec) {
        try {
            sender.send(rec).get(SEND_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.warn("outbox 発行が中断されました eventId={}", rec.eventId());
            return false;
        } catch (ExecutionException | TimeoutException e) {
            // Kafka エラーは次回周期でリトライ。published フラグは立てない。
            LOG.warn(
                    "outbox 発行失敗 eventId={} topic={}: {}",
                    rec.eventId(),
                    rec.topic(),
                    e.toString());
            return false;
        }
    }
}
