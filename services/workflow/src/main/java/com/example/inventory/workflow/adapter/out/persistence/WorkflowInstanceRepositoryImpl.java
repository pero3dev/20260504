package com.example.inventory.workflow.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Repository;

import com.example.inventory.commons.event.DomainEvent;
import com.example.inventory.commons.event.DomainEventPublisher;
import com.example.inventory.commons.persistence.OptimisticLockSupport;
import com.example.inventory.commons.tenant.TenantId;
import com.example.inventory.workflow.application.port.out.WorkflowInstanceRepository;
import com.example.inventory.workflow.domain.model.DefinitionKey;
import com.example.inventory.workflow.domain.model.StepStatus;
import com.example.inventory.workflow.domain.model.WorkflowInstance;
import com.example.inventory.workflow.domain.model.WorkflowInstanceId;
import com.example.inventory.workflow.domain.model.WorkflowStatus;
import com.example.inventory.workflow.domain.model.WorkflowStep;

@Repository
public class WorkflowInstanceRepositoryImpl implements WorkflowInstanceRepository {

    private final WorkflowInstanceMapper mapper;
    private final DomainEventPublisher eventPublisher;

    public WorkflowInstanceRepositoryImpl(
            WorkflowInstanceMapper mapper, DomainEventPublisher eventPublisher) {
        this.mapper = mapper;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public Optional<WorkflowInstance> findById(WorkflowInstanceId id) {
        WorkflowInstanceRow row = mapper.findById(id.value());
        if (row == null) return Optional.empty();
        List<WorkflowStep> steps =
                mapper.findStepsByInstanceId(row.id()).stream()
                        .map(
                                s ->
                                        new WorkflowStep(
                                                s.stepNo(),
                                                s.name(),
                                                StepStatus.valueOf(s.status()),
                                                s.startedAt(),
                                                s.completedAt(),
                                                s.error()))
                        .collect(Collectors.toList());
        return Optional.of(
                WorkflowInstance.restore(
                        new WorkflowInstanceId(row.id()),
                        new TenantId(row.tenantId()),
                        DefinitionKey.valueOf(row.definitionKey()),
                        row.businessKey(),
                        row.payloadJson(),
                        steps,
                        row.currentStep(),
                        WorkflowStatus.valueOf(row.status()),
                        row.error(),
                        row.version(),
                        row.startedAt(),
                        row.completedAt()));
    }

    @Override
    public WorkflowInstance save(WorkflowInstance aggregate) {
        WorkflowInstanceRow row =
                new WorkflowInstanceRow(
                        aggregate.id().value(),
                        aggregate.tenantId().value(),
                        aggregate.definitionKey().name(),
                        aggregate.businessKey(),
                        aggregate.payloadJson(),
                        aggregate.currentStep(),
                        aggregate.totalSteps(),
                        aggregate.status().name(),
                        aggregate.error(),
                        aggregate.version() + 1,
                        aggregate.startedAt(),
                        aggregate.completedAt());

        if (aggregate.version() == 0L) {
            mapper.insert(row);
            for (WorkflowStep s : aggregate.steps()) {
                mapper.insertStep(toStepRow(aggregate.id().value(), s));
            }
        } else {
            int affected = mapper.update(row, aggregate.version());
            OptimisticLockSupport.verify(
                    affected, "WorkflowInstance", aggregate.id().value(), aggregate.version());
            // ステップは状態遷移ごとに upsert(差分のみ updated される)。
            for (WorkflowStep s : aggregate.steps()) {
                mapper.upsertStep(toStepRow(aggregate.id().value(), s));
            }
        }

        for (DomainEvent event : aggregate.pendingEvents()) {
            eventPublisher.publish(event);
        }
        aggregate.clearPendingEvents();
        return aggregate;
    }

    @Override
    public List<WorkflowInstanceId> findStartedInstanceIdsOlderThan(Instant cutoff, int limit) {
        return mapper.findStartedIdsOlderThan(cutoff, limit).stream()
                .map(WorkflowInstanceId::new)
                .collect(Collectors.toList());
    }

    @Override
    public void delete(WorkflowInstance aggregate) {
        mapper.deleteStepsByInstanceId(aggregate.id().value());
        int affected = mapper.delete(aggregate.id().value(), aggregate.version());
        OptimisticLockSupport.verify(
                affected, "WorkflowInstance", aggregate.id().value(), aggregate.version());
    }

    private static WorkflowStepRow toStepRow(long instanceId, WorkflowStep s) {
        return new WorkflowStepRow(
                instanceId,
                s.stepNo(),
                s.name(),
                s.status().name(),
                s.startedAt(),
                s.completedAt(),
                s.error());
    }
}
