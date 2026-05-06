package com.example.inventory.workflow.application.usecase;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.inventory.commons.audit.Auditable;
import com.example.inventory.commons.persistence.SnowflakeIdGenerator;
import com.example.inventory.workflow.application.port.in.StartWorkflowUseCase;
import com.example.inventory.workflow.application.port.in.WorkflowDefinitionNotFoundException;
import com.example.inventory.workflow.application.port.out.WorkflowInstanceRepository;
import com.example.inventory.workflow.domain.definition.WorkflowDefinition;
import com.example.inventory.workflow.domain.definition.WorkflowDefinitionRegistry;
import com.example.inventory.workflow.domain.model.WorkflowInstance;
import com.example.inventory.workflow.domain.model.WorkflowInstanceId;

@Service
public class StartWorkflowService implements StartWorkflowUseCase {

    private final WorkflowInstanceRepository repository;
    private final WorkflowDefinitionRegistry registry;
    private final SnowflakeIdGenerator idGenerator;

    public StartWorkflowService(
            WorkflowInstanceRepository repository,
            WorkflowDefinitionRegistry registry,
            SnowflakeIdGenerator idGenerator) {
        this.repository = repository;
        this.registry = registry;
        this.idGenerator = idGenerator;
    }

    @Override
    @Transactional
    @Auditable(
            action = "WORKFLOW_START",
            targetType = "WorkflowInstance",
            targetIdExpression = "#command.businessKey")
    public WorkflowInstance start(Command command) {
        WorkflowDefinition def =
                registry.find(command.definitionKey())
                        .orElseThrow(
                                () ->
                                        new WorkflowDefinitionNotFoundException(
                                                command.definitionKey().name()));

        WorkflowInstance instance =
                WorkflowInstance.start(
                        new WorkflowInstanceId(idGenerator.nextId()),
                        command.tenantId(),
                        command.definitionKey(),
                        command.businessKey(),
                        command.payloadJson(),
                        def.stepNames());
        return repository.save(instance);
    }
}
