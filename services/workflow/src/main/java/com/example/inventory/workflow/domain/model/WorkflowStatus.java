package com.example.inventory.workflow.domain.model;

/**
 * ワークフローインスタンスのステータス。
 *
 * <p>遷移:
 *
 * <pre>
 *   STARTED ─completeCurrentStep(最終)─▶ COMPLETED
 *      │
 *      ├─failCurrentStep()─▶ FAILED
 *      └─cancel()──────────▶ CANCELLED
 * </pre>
 */
public enum WorkflowStatus {
    STARTED,
    COMPLETED,
    FAILED,
    CANCELLED
}
