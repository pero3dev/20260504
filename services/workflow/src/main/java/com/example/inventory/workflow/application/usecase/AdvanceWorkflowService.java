package com.example.inventory.workflow.application.usecase;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.inventory.commons.audit.Auditable;
import com.example.inventory.workflow.application.port.in.AdvanceWorkflowUseCase;
import com.example.inventory.workflow.application.port.in.WorkflowNotFoundException;
import com.example.inventory.workflow.application.port.in.WorkflowStateConflictException;
import com.example.inventory.workflow.application.port.out.WorkflowInstanceRepository;
import com.example.inventory.workflow.domain.model.WorkflowInstance;
import com.example.inventory.workflow.domain.model.WorkflowInstanceId;

@Service
public class AdvanceWorkflowService implements AdvanceWorkflowUseCase {

    private final WorkflowInstanceRepository repository;

    public AdvanceWorkflowService(WorkflowInstanceRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    @Auditable(
            action = "WORKFLOW_STEP_COMPLETE",
            targetType = "WorkflowInstance",
            targetIdExpression = "#workflowId")
    public WorkflowInstance completeCurrent(long workflowId) {
        WorkflowInstance instance = load(workflowId);
        try {
            instance.completeCurrentStep();
        } catch (IllegalStateException e) {
            throw new WorkflowStateConflictException(e.getMessage());
        }
        return repository.save(instance);
    }

    @Override
    @Transactional
    @Auditable(
            action = "WORKFLOW_STEP_FAIL",
            targetType = "WorkflowInstance",
            targetIdExpression = "#workflowId")
    public WorkflowInstance failCurrent(long workflowId, String reason) {
        WorkflowInstance instance = load(workflowId);
        try {
            instance.failCurrentStep(reason);
        } catch (IllegalStateException e) {
            throw new WorkflowStateConflictException(e.getMessage());
        }
        return repository.save(instance);
    }

    @Override
    @Transactional
    @Auditable(
            action = "WORKFLOW_CANCEL",
            targetType = "WorkflowInstance",
            targetIdExpression = "#workflowId")
    public WorkflowInstance cancel(long workflowId, String reason) {
        WorkflowInstance instance = load(workflowId);
        try {
            instance.cancel(reason);
        } catch (IllegalStateException e) {
            throw new WorkflowStateConflictException(e.getMessage());
        }
        return repository.save(instance);
    }

    private WorkflowInstance load(long workflowId) {
        return repository
                .findById(new WorkflowInstanceId(workflowId))
                .orElseThrow(() -> new WorkflowNotFoundException(workflowId));
    }
}
