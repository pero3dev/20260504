package com.example.inventory.workflow.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.inventory.commons.event.DomainEvent;
import com.example.inventory.commons.tenant.TenantId;
import com.example.inventory.workflow.domain.event.WorkflowInstanceCompletedEvent;

class WorkflowInstanceTest {

    private static final TenantId TENANT = new TenantId("tenant-1");
    private static final List<String> STEPS = List.of("VALIDATE", "APPROVE", "NOTIFY");

    @Test
    void start_は_全ステップ_PENDING_のうち_step1_のみ_RUNNING_でイベント未発行() {
        WorkflowInstance instance = newStarted();

        assertThat(instance.status()).isEqualTo(WorkflowStatus.STARTED);
        assertThat(instance.currentStep()).isEqualTo(1);
        assertThat(instance.steps().get(0).status()).isEqualTo(StepStatus.RUNNING);
        assertThat(instance.steps().get(1).status()).isEqualTo(StepStatus.PENDING);
        assertThat(instance.steps().get(2).status()).isEqualTo(StepStatus.PENDING);
        assertThat(instance.pendingEvents()).isEmpty();
    }

    @Test
    void completeCurrentStep_は次の_PENDING_を_RUNNING_に進める() {
        WorkflowInstance instance = newStarted();
        instance.completeCurrentStep();

        assertThat(instance.status()).isEqualTo(WorkflowStatus.STARTED);
        assertThat(instance.currentStep()).isEqualTo(2);
        assertThat(instance.steps().get(0).status()).isEqualTo(StepStatus.COMPLETED);
        assertThat(instance.steps().get(1).status()).isEqualTo(StepStatus.RUNNING);
    }

    @Test
    void 最終ステップ完了で_全体が_COMPLETED_になり_イベント発行() {
        WorkflowInstance instance = newStarted();
        instance.completeCurrentStep(); // VALIDATE
        instance.completeCurrentStep(); // APPROVE
        instance.completeCurrentStep(); // NOTIFY (last)

        assertThat(instance.status()).isEqualTo(WorkflowStatus.COMPLETED);
        assertThat(instance.completedAt()).isNotNull();
        List<DomainEvent> events = instance.pendingEvents();
        assertThat(events).hasSize(1);
        WorkflowInstanceCompletedEvent ev = (WorkflowInstanceCompletedEvent) events.get(0);
        assertThat(ev.finalStatus()).isEqualTo(WorkflowStatus.COMPLETED);
    }

    @Test
    void failCurrentStep_で_全体が_FAILED_になり_イベント発行() {
        WorkflowInstance instance = newStarted();
        instance.completeCurrentStep(); // VALIDATE -> APPROVE
        instance.failCurrentStep("拒否");

        assertThat(instance.status()).isEqualTo(WorkflowStatus.FAILED);
        assertThat(instance.error()).isEqualTo("拒否");
        WorkflowInstanceCompletedEvent ev =
                (WorkflowInstanceCompletedEvent) instance.pendingEvents().get(0);
        assertThat(ev.finalStatus()).isEqualTo(WorkflowStatus.FAILED);
    }

    @Test
    void cancel_で_RUNNING_中のステップは_FAILED_扱いで閉じて_全体_CANCELLED() {
        WorkflowInstance instance = newStarted();
        instance.cancel("ユーザー取消");

        assertThat(instance.status()).isEqualTo(WorkflowStatus.CANCELLED);
        assertThat(instance.steps().get(0).status()).isEqualTo(StepStatus.FAILED);
        WorkflowInstanceCompletedEvent ev =
                (WorkflowInstanceCompletedEvent) instance.pendingEvents().get(0);
        assertThat(ev.finalStatus()).isEqualTo(WorkflowStatus.CANCELLED);
    }

    @Test
    void cancel_は_冪等() {
        WorkflowInstance instance = newStarted();
        instance.cancel("first");
        instance.clearPendingEvents();
        instance.cancel("second"); // no-op

        assertThat(instance.status()).isEqualTo(WorkflowStatus.CANCELLED);
        assertThat(instance.pendingEvents()).isEmpty();
    }

    @Test
    void COMPLETED_状態のインスタンスに_completeCurrentStep_は_IllegalState() {
        WorkflowInstance instance = newStarted();
        for (int i = 0; i < STEPS.size(); i++) instance.completeCurrentStep();

        assertThatThrownBy(instance::completeCurrentStep).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void FAILED_状態のインスタンスに_completeCurrentStep_は_IllegalState() {
        WorkflowInstance instance = newStarted();
        instance.failCurrentStep("err");

        assertThatThrownBy(instance::completeCurrentStep).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void expireIfOverdue_は_SLA_未満なら_状態を変えず_false() {
        WorkflowInstance instance = newStarted();
        boolean expired = instance.expireIfOverdue(Duration.ofHours(1), Instant.now());

        assertThat(expired).isFalse();
        assertThat(instance.status()).isEqualTo(WorkflowStatus.STARTED);
        assertThat(instance.pendingEvents()).isEmpty();
    }

    @Test
    void expireIfOverdue_は_SLA_超過なら_FAILED_遷移して_イベント発行() {
        WorkflowInstance instance = newStarted();
        Instant farFuture = instance.startedAt().plus(Duration.ofDays(2));
        boolean expired = instance.expireIfOverdue(Duration.ofHours(24), farFuture);

        assertThat(expired).isTrue();
        assertThat(instance.status()).isEqualTo(WorkflowStatus.FAILED);
        assertThat(instance.steps().get(0).status()).isEqualTo(StepStatus.FAILED);
        assertThat(instance.error()).contains("SLA timeout");
        assertThat(instance.pendingEvents()).hasSize(1);
        assertThat(((WorkflowInstanceCompletedEvent) instance.pendingEvents().get(0)).finalStatus())
                .isEqualTo(WorkflowStatus.FAILED);
    }

    @Test
    void expireIfOverdue_は_SLA_ZERO_なら_no_op() {
        WorkflowInstance instance = newStarted();
        Instant farFuture = instance.startedAt().plus(Duration.ofDays(365));
        boolean expired = instance.expireIfOverdue(Duration.ZERO, farFuture);

        assertThat(expired).isFalse();
        assertThat(instance.status()).isEqualTo(WorkflowStatus.STARTED);
    }

    @Test
    void expireIfOverdue_は_既に_COMPLETED_状態なら_no_op() {
        WorkflowInstance instance = newStarted();
        instance.completeCurrentStep();
        instance.completeCurrentStep();
        instance.completeCurrentStep(); // → COMPLETED
        instance.clearPendingEvents();

        Instant farFuture = instance.startedAt().plus(Duration.ofDays(365));
        boolean expired = instance.expireIfOverdue(Duration.ofHours(1), farFuture);

        assertThat(expired).isFalse();
        assertThat(instance.status()).isEqualTo(WorkflowStatus.COMPLETED);
    }

    @Test
    void stepNames_空は_start_時に_IllegalArgument() {
        assertThatThrownBy(
                        () ->
                                WorkflowInstance.start(
                                        new WorkflowInstanceId(1L),
                                        TENANT,
                                        DefinitionKey.APPROVAL_FLOW,
                                        "biz-1",
                                        "{}",
                                        List.of()))
                .isInstanceOf(IllegalArgumentException.class);
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
