package com.example.inventory.workflow.domain.model;

/**
 * ワークフロー定義キー。MVP は静的(コード定義)、将来 DB 駆動 / BPMN 化を想定して 型を別に切ってある。
 *
 * <p>新しい定義を増やす時は: enum に追加 + 対応する {@link WorkflowDefinition} 実装を Spring Bean として登録する。
 */
public enum DefinitionKey {
    /** 承認フロー(ADR-0015 の代表サンプル)。VALIDATE → APPROVE → NOTIFY の 3 ステップ。 */
    APPROVAL_FLOW
}
