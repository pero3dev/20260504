package com.example.inventory.workflow.domain.definition;

import java.time.Duration;
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

    /**
     * インスタンス全体の SLA(B2 で導入)。 {@link #startedAt から本期限を超過すると {@code
     * ExpireOverdueWorkflowsService} が FAILED に強制遷移させ、 {@code WorkflowInstanceCompletedEvent} を発行する。
     *
     * <p>{@link Duration#ZERO} を返すと SLA 無効(timeout しない)。 デフォルトは ZERO で、 各定義は要件に応じて override する。
     */
    default Duration instanceSla() {
        return Duration.ZERO;
    }
}
