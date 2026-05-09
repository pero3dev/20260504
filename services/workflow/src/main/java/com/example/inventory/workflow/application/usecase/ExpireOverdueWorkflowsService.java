package com.example.inventory.workflow.application.usecase;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.inventory.commons.audit.AuditExempt;
import com.example.inventory.workflow.application.port.in.ExpireOverdueWorkflowsUseCase;
import com.example.inventory.workflow.application.port.out.WorkflowInstanceRepository;
import com.example.inventory.workflow.domain.definition.WorkflowDefinition;
import com.example.inventory.workflow.domain.definition.WorkflowDefinitionRegistry;
import com.example.inventory.workflow.domain.model.WorkflowInstance;
import com.example.inventory.workflow.domain.model.WorkflowInstanceId;

/**
 * SLA 超過した STARTED インスタンスを FAILED に強制遷移させるサービス(ADR-0021 B2)。
 *
 * <p>scheduler から呼ばれる時間経過バッチ処理:
 *
 * <ol>
 *   <li>「SLA の最大値より古い」STARTED インスタンスを ID で 1 batch 取得(repository finder)
 *   <li>各 ID を {@link WorkflowInstanceRepository#findById} で reload
 *   <li>定義の {@code instanceSla} を取得し、 {@link WorkflowInstance#expireIfOverdue} を呼出
 *   <li>状態遷移したものは save(Outbox 経由で {@code workflow.instance.completed.v1} 発行)
 * </ol>
 *
 * <p>1 件失敗が他に波及しないよう各インスタンスは独立した処理にする(例外は catch して次へ)。 楽観ロックで版が古いと save 失敗するが、 これも catch
 * してログのみ(次回スキャンで再試行)。
 *
 * <p>per-instance に独立した TX を貼る({@link TransactionTemplate} を使用)。 自己 invocation 経由の
 * {@code @Transactional} はプロキシを通らず効かないため明示的に programmatic TX を採用。 batch 全体を 1 TX で囲うと長時間ロックの懸念があるため
 * 意図的に避ける。
 */
@Service
public class ExpireOverdueWorkflowsService implements ExpireOverdueWorkflowsUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(ExpireOverdueWorkflowsService.class);

    /** 1 batch あたりの最大処理件数。 thundering-herd を避けるため上限を設ける。 */
    static final int BATCH_LIMIT = 100;

    private final WorkflowInstanceRepository repository;
    private final WorkflowDefinitionRegistry registry;
    private final TransactionTemplate transactionTemplate;

    public ExpireOverdueWorkflowsService(
            WorkflowInstanceRepository repository,
            WorkflowDefinitionRegistry registry,
            PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.registry = registry;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    @AuditExempt(
            reason =
                    "scheduler 起動の SLA 超過 housekeeping。 ユーザ操作ではなく時間経過の副作用で、"
                            + " 統制上は scheduler の責務(個別 expire は INFO ログで監視)")
    public int expireOverdue() {
        Instant now = Instant.now();
        // 「最も短い SLA」より古いインスタンスのみ scan する。 全部 0 なら何も処理しない。
        Duration smallestSla = smallestPositiveSla();
        if (smallestSla == null) {
            return 0;
        }
        Instant cutoff = now.minus(smallestSla);
        List<WorkflowInstanceId> candidateIds =
                repository.findStartedInstanceIdsOlderThan(cutoff, BATCH_LIMIT);
        if (candidateIds.isEmpty()) {
            return 0;
        }
        int expired = 0;
        for (WorkflowInstanceId id : candidateIds) {
            try {
                if (expireOne(id, now)) {
                    expired++;
                }
            } catch (RuntimeException e) {
                LOG.warn("Workflow {} の SLA 失効処理に失敗、 次回再試行: {}", id.value(), e.toString());
            }
        }
        if (expired > 0) {
            LOG.info("SLA scheduler: 候補 {} 件中 {} 件を FAILED に遷移", candidateIds.size(), expired);
        }
        return expired;
    }

    boolean expireOne(WorkflowInstanceId id, Instant now) {
        Boolean result =
                transactionTemplate.execute(
                        status -> {
                            Optional<WorkflowInstance> opt = repository.findById(id);
                            if (opt.isEmpty()) return false;
                            WorkflowInstance instance = opt.get();
                            WorkflowDefinition def =
                                    registry.find(instance.definitionKey()).orElse(null);
                            if (def == null) {
                                // 定義が無くなったケース(コード変更で削除等) — 観察ログのみで安全側にスキップ。
                                LOG.warn(
                                        "Workflow {} の definition '{}' が registry に無いため SLA 判定スキップ",
                                        id.value(),
                                        instance.definitionKey().name());
                                return false;
                            }
                            boolean expired = instance.expireIfOverdue(def.instanceSla(), now);
                            if (!expired) return false;
                            repository.save(instance);
                            LOG.info(
                                    "Workflow {} を SLA 超過で FAILED に遷移 definition={} startedAt={} sla={}",
                                    id.value(),
                                    instance.definitionKey().name(),
                                    instance.startedAt(),
                                    def.instanceSla());
                            return true;
                        });
        return Boolean.TRUE.equals(result);
    }

    private Duration smallestPositiveSla() {
        Duration smallest = null;
        for (com.example.inventory.workflow.domain.model.DefinitionKey key :
                com.example.inventory.workflow.domain.model.DefinitionKey.values()) {
            Optional<WorkflowDefinition> def = registry.find(key);
            if (def.isEmpty()) continue;
            Duration sla = def.get().instanceSla();
            if (sla == null || sla.isZero() || sla.isNegative()) continue;
            if (smallest == null || sla.compareTo(smallest) < 0) {
                smallest = sla;
            }
        }
        return smallest;
    }
}
