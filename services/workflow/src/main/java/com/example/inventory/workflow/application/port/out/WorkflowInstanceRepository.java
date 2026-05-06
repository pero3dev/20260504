package com.example.inventory.workflow.application.port.out;

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
}
