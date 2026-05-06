package com.example.inventory.workflow.domain.definition;

import java.util.List;

import com.example.inventory.workflow.domain.model.DefinitionKey;

/**
 * ワークフロー定義の抽象。MVP は静的(コードで定義)、将来 DB / BPMN 駆動に進化させる前提で 抽象を切ってある。
 *
 * <p>各実装はステップ名のリストを返す。インスタンス生成時にスナップショットされて 永続化されるため、定義を変更しても既存インスタンスは影響を受けない。
 */
public interface WorkflowDefinition {

    DefinitionKey key();

    /** ステップ名のリスト(順序保存)。1 始まりの stepNo に対応する。空リストは不可。 */
    List<String> stepNames();
}
