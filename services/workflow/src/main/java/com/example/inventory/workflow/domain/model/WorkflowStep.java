package com.example.inventory.workflow.domain.model;

import java.time.Instant;

/**
 * ワークフローステップ(集約 {@link WorkflowInstance} 内の Value Object)。
 *
 * <p>不変。状態遷移は新しい {@code WorkflowStep} を生成して置き換える(集約側で管理)。
 */
public record WorkflowStep(
        int stepNo,
        String name,
        StepStatus status,
        Instant startedAt,
        Instant completedAt,
        String error) {

    public WorkflowStep {
        if (stepNo < 1) throw new IllegalArgumentException("stepNo は 1 以上");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name は必須");
        if (status == null) throw new IllegalArgumentException("status は必須");
    }

    /** PENDING 初期ステップを作る(WorkflowInstance.start で利用)。 */
    public static WorkflowStep pending(int stepNo, String name) {
        return new WorkflowStep(stepNo, name, StepStatus.PENDING, null, null, null);
    }

    public WorkflowStep markRunning(Instant now) {
        return new WorkflowStep(stepNo, name, StepStatus.RUNNING, now, null, null);
    }

    public WorkflowStep markCompleted(Instant now) {
        return new WorkflowStep(stepNo, name, StepStatus.COMPLETED, startedAt, now, null);
    }

    public WorkflowStep markFailed(Instant now, String reason) {
        return new WorkflowStep(stepNo, name, StepStatus.FAILED, startedAt, now, reason);
    }
}
