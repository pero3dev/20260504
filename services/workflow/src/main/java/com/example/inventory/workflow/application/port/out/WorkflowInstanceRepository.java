package com.example.inventory.workflow.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.example.inventory.commons.persistence.AggregateRepository;
import com.example.inventory.workflow.domain.model.WorkflowInstance;
import com.example.inventory.workflow.domain.model.WorkflowInstanceId;

public interface WorkflowInstanceRepository
        extends AggregateRepository<WorkflowInstance, WorkflowInstanceId> {

    @Override
    Optional<WorkflowInstance> findById(WorkflowInstanceId id);

    @Override
    WorkflowInstance save(WorkflowInstance aggregate);

    @Override
    void delete(WorkflowInstance aggregate);

    /**
     * STARTED 状態かつ {@code startedAt < cutoff} のインスタンス ID を最大 {@code limit} 件返す(B2 SLA scheduler 用)。
     *
     * <p>full aggregate ではなく ID のみを返すので、 呼出側は {@link #findById} で都度 reload してから {@link
     * WorkflowInstance#expireIfOverdue} を適用する。 これにより楽観ロック検証が効く + 1 件失敗が他に波及しない。
     *
     * <p>Pool 方式マルチテナンシ(ADR-0003)で workflow service は全 tenant を 1 DB に持つので、 本クエリは tenant 横断的にスキャンする
     * **system-level operation**。 各 instance の tenantId は restore 時に再構築されるため、 後続の event 発行 / audit
     * は instance 自身の tenantId に紐づく。
     */
    List<WorkflowInstanceId> findStartedInstanceIdsOlderThan(Instant cutoff, int limit);
}
