package com.example.inventory.workflow.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.example.inventory.commons.tenant.TenantId;
import com.example.inventory.workflow.application.port.in.WorkflowNotFoundException;
import com.example.inventory.workflow.application.port.in.WorkflowStateConflictException;
import com.example.inventory.workflow.application.port.out.WorkflowInstanceRepository;
import com.example.inventory.workflow.domain.model.DefinitionKey;
import com.example.inventory.workflow.domain.model.WorkflowInstance;
import com.example.inventory.workflow.domain.model.WorkflowInstanceId;
import com.example.inventory.workflow.domain.model.WorkflowStatus;

class AdvanceWorkflowServiceTest {

    private static final TenantId TENANT = new TenantId("tenant-1");
    private static final List<String> STEPS = List.of("VALIDATE", "APPROVE");

    private WorkflowInstanceRepository repository;
    private AdvanceWorkflowService service;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(WorkflowInstanceRepository.class);
        when(repository.save(any(WorkflowInstance.class))).thenAnswer(inv -> inv.getArgument(0));
        service = new AdvanceWorkflowService(repository);
    }

    @Test
    void completeCurrent_は_RUNNING_ステップを完了させて_save_する() {
        WorkflowInstance instance = newStarted();
        when(repository.findById(new WorkflowInstanceId(1L))).thenReturn(Optional.of(instance));

        WorkflowInstance result = service.completeCurrent(1L);

        assertThat(result.currentStep()).isEqualTo(2);
        verify(repository).save(instance);
    }

    @Test
    void インスタンス不在で_completeCurrent_は_WorkflowNotFoundException() {
        when(repository.findById(new WorkflowInstanceId(1L))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.completeCurrent(1L))
                .isInstanceOf(WorkflowNotFoundException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void COMPLETED_インスタンスに_completeCurrent_は_WorkflowStateConflictException() {
        WorkflowInstance instance = newStarted();
        instance.completeCurrentStep(); // VALIDATE
        instance.completeCurrentStep(); // APPROVE -> COMPLETED
        when(repository.findById(new WorkflowInstanceId(1L))).thenReturn(Optional.of(instance));

        assertThatThrownBy(() -> service.completeCurrent(1L))
                .isInstanceOf(WorkflowStateConflictException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void failCurrent_は_全体_FAILED_に_save() {
        WorkflowInstance instance = newStarted();
        when(repository.findById(new WorkflowInstanceId(1L))).thenReturn(Optional.of(instance));

        WorkflowInstance result = service.failCurrent(1L, "reject");

        assertThat(result.status()).isEqualTo(WorkflowStatus.FAILED);
        assertThat(result.error()).isEqualTo("reject");
    }

    @Test
    void cancel_は_全体_CANCELLED_に_save() {
        WorkflowInstance instance = newStarted();
        when(repository.findById(new WorkflowInstanceId(1L))).thenReturn(Optional.of(instance));

        WorkflowInstance result = service.cancel(1L, "user requested");

        assertThat(result.status()).isEqualTo(WorkflowStatus.CANCELLED);
    }

    private static WorkflowInstance newStarted() {
        return WorkflowInstance.start(
                new WorkflowInstanceId(1L),
                TENANT,
                DefinitionKey.APPROVAL_FLOW,
                "biz-1",
                "{}",
                STEPS);
    }
}
