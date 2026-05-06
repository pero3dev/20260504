package com.example.inventory.workflow.application.usecase;

import org.springframework.stereotype.Service;

import com.example.inventory.workflow.application.port.in.GetWorkflowUseCase;
import com.example.inventory.workflow.application.port.in.WorkflowNotFoundException;
import com.example.inventory.workflow.application.port.out.WorkflowInstanceRepository;
import com.example.inventory.workflow.domain.model.WorkflowInstance;
import com.example.inventory.workflow.domain.model.WorkflowInstanceId;

@Service
public class GetWorkflowService implements GetWorkflowUseCase {

    private final WorkflowInstanceRepository repository;

    public GetWorkflowService(WorkflowInstanceRepository repository) {
        this.repository = repository;
    }

    @Override
    public WorkflowInstance get(long workflowId) {
        return repository
                .findById(new WorkflowInstanceId(workflowId))
                .orElseThrow(() -> new WorkflowNotFoundException(workflowId));
    }
}
