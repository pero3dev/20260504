package com.example.inventory.workflow.adapter.in.rest;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.example.inventory.commons.tenant.TenantContext;
import com.example.inventory.workflow.adapter.in.rest.api.WorkflowsApi;
import com.example.inventory.workflow.adapter.in.rest.api.model.StartWorkflowRequest;
import com.example.inventory.workflow.adapter.in.rest.api.model.StepFailureRequest;
import com.example.inventory.workflow.adapter.in.rest.api.model.WorkflowResponse;
import com.example.inventory.workflow.adapter.in.rest.api.model.WorkflowStepResponse;
import com.example.inventory.workflow.application.port.in.AdvanceWorkflowUseCase;
import com.example.inventory.workflow.application.port.in.GetWorkflowUseCase;
import com.example.inventory.workflow.application.port.in.StartWorkflowUseCase;
import com.example.inventory.workflow.domain.model.DefinitionKey;
import com.example.inventory.workflow.domain.model.WorkflowInstance;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Workflow REST 入力アダプタ。OpenAPI 生成 {@link WorkflowsApi} を実装(ADR-0006)。 */
@RestController
public class WorkflowController implements WorkflowsApi {

    private final StartWorkflowUseCase startWorkflow;
    private final AdvanceWorkflowUseCase advanceWorkflow;
    private final GetWorkflowUseCase getWorkflow;
    private final ObjectMapper objectMapper;

    public WorkflowController(
            StartWorkflowUseCase startWorkflow,
            AdvanceWorkflowUseCase advanceWorkflow,
            GetWorkflowUseCase getWorkflow,
            ObjectMapper objectMapper) {
        this.startWorkflow = startWorkflow;
        this.advanceWorkflow = advanceWorkflow;
        this.getWorkflow = getWorkflow;
        this.objectMapper = objectMapper;
    }

    @Override
    public ResponseEntity<WorkflowResponse> startWorkflow(StartWorkflowRequest request) {
        String payloadJson = serialize(request.getPayload());
        WorkflowInstance instance =
                startWorkflow.start(
                        new StartWorkflowUseCase.Command(
                                TenantContext.required(),
                                DefinitionKey.valueOf(request.getDefinitionKey().getValue()),
                                request.getBusinessKey(),
                                payloadJson));
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(instance));
    }

    @Override
    public ResponseEntity<WorkflowResponse> getWorkflow(Long workflowId) {
        return ResponseEntity.ok(toResponse(getWorkflow.get(workflowId)));
    }

    @Override
    public ResponseEntity<WorkflowResponse> completeWorkflowStep(Long workflowId) {
        return ResponseEntity.ok(toResponse(advanceWorkflow.completeCurrent(workflowId)));
    }

    @Override
    public ResponseEntity<WorkflowResponse> failWorkflowStep(
            Long workflowId, StepFailureRequest request) {
        return ResponseEntity.ok(
                toResponse(advanceWorkflow.failCurrent(workflowId, request.getReason())));
    }

    @Override
    public ResponseEntity<WorkflowResponse> cancelWorkflow(
            Long workflowId, StepFailureRequest request) {
        return ResponseEntity.ok(
                toResponse(advanceWorkflow.cancel(workflowId, request.getReason())));
    }

    private String serialize(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) return "{}";
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("payload を JSON 化できません", e);
        }
    }

    private static WorkflowResponse toResponse(WorkflowInstance instance) {
        WorkflowResponse r = new WorkflowResponse();
        r.setId(instance.id().value());
        r.setDefinitionKey(instance.definitionKey().name());
        r.setBusinessKey(instance.businessKey());
        r.setStatus(WorkflowResponse.StatusEnum.valueOf(instance.status().name()));
        r.setCurrentStep(instance.currentStep());
        r.setTotalSteps(instance.totalSteps());
        r.setError(instance.error());
        r.setStartedAt(OffsetDateTime.ofInstant(instance.startedAt(), ZoneOffset.UTC));
        r.setCompletedAt(
                instance.completedAt() == null
                        ? null
                        : OffsetDateTime.ofInstant(instance.completedAt(), ZoneOffset.UTC));
        r.setSteps(
                instance.steps().stream()
                        .map(
                                s -> {
                                    WorkflowStepResponse sr = new WorkflowStepResponse();
                                    sr.setStepNo(s.stepNo());
                                    sr.setName(s.name());
                                    sr.setStatus(
                                            WorkflowStepResponse.StatusEnum.valueOf(
                                                    s.status().name()));
                                    sr.setStartedAt(
                                            s.startedAt() == null
                                                    ? null
                                                    : OffsetDateTime.ofInstant(
                                                            s.startedAt(), ZoneOffset.UTC));
                                    sr.setCompletedAt(
                                            s.completedAt() == null
                                                    ? null
                                                    : OffsetDateTime.ofInstant(
                                                            s.completedAt(), ZoneOffset.UTC));
                                    sr.setError(s.error());
                                    return sr;
                                })
                        .collect(Collectors.toList()));
        r.setVersion(instance.version() + 1);
        return r;
    }
}
