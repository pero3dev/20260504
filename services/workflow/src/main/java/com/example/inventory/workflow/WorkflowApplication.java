package com.example.inventory.workflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Workflow Service — Saga オーケストレータ MVP(ADR-0015)。
 *
 * <p>ADR-0015 で凍結した判定基準:
 *
 * <ul>
 *   <li>ステップ数 ≥ 4 / SLA タイムアウト / 進行可視化 / 承認 / 条件分岐 のいずれかを満たすフローのみ 本サービスで書く。choreography
 *       で済むものは各サービス側で listener で実装する。
 * </ul>
 *
 * <p>本 MVP のスコープ:
 *
 * <ul>
 *   <li>静的 WorkflowDefinition(コードで定義、enum + ステップ列)
 *   <li>WorkflowInstance の DB 永続化(workflow_instance + workflow_step)
 *   <li>REST API でステップ進行(start / completeStep / failStep / cancel / get)
 *   <li>完了時に {@code workflow.instance.completed.v1} を Outbox 経由で発行
 *   <li>Pool 方式マルチテナンシ(共通DB + tenant_id 列、ADR-0003)
 * </ul>
 *
 * <p>Phase 2 以降:
 *
 * <ul>
 *   <li>自動 step handler(各ステップを Bean として実装、エンジンが順次呼出)
 *   <li>SLA タイムアウト + エスカレーション
 *   <li>多段補償(失敗時に前ステップの補償を順番に呼ぶ)
 *   <li>進行可視化 UI / Datadog ダッシュボード
 *   <li>動的 Definition(DB / BPMN ファイル)
 * </ul>
 */
@SpringBootApplication
@EnableScheduling
public class WorkflowApplication {

    public static void main(String[] args) {
        SpringApplication.run(WorkflowApplication.class, args);
    }
}
