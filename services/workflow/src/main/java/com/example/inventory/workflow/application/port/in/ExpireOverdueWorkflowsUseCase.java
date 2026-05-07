package com.example.inventory.workflow.application.port.in;

/**
 * SLA 超過した STARTED インスタンスを FAILED に強制遷移させるユースケース(ADR-0021 B2)。
 *
 * <p>{@code WorkflowSlaScheduler} が定期的に呼び出す。 各定義の {@code instanceSla} を見て、 {@code now - startedAt >
 * sla} のインスタンスをバッチ処理する。
 */
public interface ExpireOverdueWorkflowsUseCase {

    /**
     * SLA 超過インスタンスを 1 batch ぶん expire する。
     *
     * @return 状態遷移したインスタンス件数
     */
    int expireOverdue();
}
