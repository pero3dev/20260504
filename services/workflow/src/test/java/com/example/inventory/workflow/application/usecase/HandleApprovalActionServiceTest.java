package com.example.inventory.workflow.application.usecase;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import com.example.inventory.workflow.application.port.in.AdvanceWorkflowUseCase;
import com.example.inventory.workflow.application.port.in.HandleApprovalActionUseCase;
import com.example.inventory.workflow.domain.model.ApprovalAction;

class HandleApprovalActionServiceTest {

    private AdvanceWorkflowUseCase advance;
    private HandleApprovalActionService service;

    @BeforeEach
    void setUp() {
        advance = Mockito.mock(AdvanceWorkflowUseCase.class);
        service = new HandleApprovalActionService(advance);
    }

    @Test
    void APPROVE_は_completeCurrent_を呼ぶ() {
        service.handle(
                new HandleApprovalActionUseCase.Command(
                        100L, ApprovalAction.APPROVE, "alice", null));

        verify(advance).completeCurrent(100L);
        verify(advance, never()).failCurrent(Mockito.anyLong(), Mockito.anyString());
    }

    @Test
    void SKIP_も_completeCurrent_を呼ぶ_REJECT_と区別したいケースのため_enum_は分かれているが_step_進行は同じ() {
        service.handle(
                new HandleApprovalActionUseCase.Command(
                        100L, ApprovalAction.SKIP, "alice", "skipped"));

        verify(advance).completeCurrent(100L);
        verify(advance, never()).failCurrent(Mockito.anyLong(), Mockito.anyString());
    }

    @Test
    void REJECT_は_failCurrent_を_actor_と_comment_付きで呼ぶ() {
        service.handle(
                new HandleApprovalActionUseCase.Command(
                        100L, ApprovalAction.REJECT, "bob", "金額超過"));

        ArgumentCaptor<String> reasonCaptor = ArgumentCaptor.forClass(String.class);
        verify(advance).failCurrent(Mockito.eq(100L), reasonCaptor.capture());
        String reason = reasonCaptor.getValue();
        org.assertj.core.api.Assertions.assertThat(reason).contains("rejected by bob");
        org.assertj.core.api.Assertions.assertThat(reason).contains("金額超過");
    }

    @Test
    void REJECT_の_comment_が_null_でも_actor_だけは_reason_に乗る() {
        service.handle(
                new HandleApprovalActionUseCase.Command(100L, ApprovalAction.REJECT, "bob", null));

        ArgumentCaptor<String> reasonCaptor = ArgumentCaptor.forClass(String.class);
        verify(advance).failCurrent(Mockito.eq(100L), reasonCaptor.capture());
        org.assertj.core.api.Assertions.assertThat(reasonCaptor.getValue())
                .isEqualTo("rejected by bob");
    }
}
