package com.example.inventory.workflow.application.port.in;

import com.example.inventory.workflow.domain.model.WorkflowInstance;

/**
 * 進行中インスタンスの現在ステップを進めるユースケース。
 *
 * <p>3 種の遷移を持つ:
 *
 * <ul>
 *   <li>{@link #completeCurrent}: 現ステップ完了。次があれば次へ、無ければ全体完了
 *   <li>{@link #failCurrent}: 現ステップ失敗 = 全体失敗(MVP は補償なし、DLQ で観察)
 *   <li>{@link #cancel}: 全体キャンセル(進行中ステップは FAILED 扱いで閉じる)
 * </ul>
 */
public interface AdvanceWorkflowUseCase {

    WorkflowInstance completeCurrent(long workflowId);

    WorkflowInstance failCurrent(long workflowId, String reason);

    WorkflowInstance cancel(long workflowId, String reason);
}
