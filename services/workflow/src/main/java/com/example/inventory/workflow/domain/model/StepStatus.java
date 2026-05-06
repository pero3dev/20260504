package com.example.inventory.workflow.domain.model;

/**
 * ステップのステータス。
 *
 * <ul>
 *   <li>PENDING: まだ着手していない(初期状態)
 *   <li>RUNNING: 現在実行中(同時に 1 ステップのみ)
 *   <li>COMPLETED: 正常完了
 *   <li>FAILED: 失敗(本ステップで Workflow も FAILED)
 *   <li>SKIPPED: 条件分岐などで飛ばされた(MVP 未使用)
 * </ul>
 */
public enum StepStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    SKIPPED
}
