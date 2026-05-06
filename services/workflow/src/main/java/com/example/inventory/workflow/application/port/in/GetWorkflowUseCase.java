package com.example.inventory.workflow.application.port.in;

import com.example.inventory.workflow.domain.model.WorkflowInstance;

public interface GetWorkflowUseCase {

    WorkflowInstance get(long workflowId);
}
