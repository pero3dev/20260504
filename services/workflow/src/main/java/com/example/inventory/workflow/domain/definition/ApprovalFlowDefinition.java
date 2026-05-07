package com.example.inventory.workflow.domain.definition;

import java.time.Duration;
import java.util.List;

import org.springframework.stereotype.Component;

import com.example.inventory.workflow.domain.model.DefinitionKey;

/**
 * ADR-0015 で例示した「承認フロー」の MVP 定義。
 *
 * <ul>
 *   <li>VALIDATE: 業務側で入力値・与信枠等のバリデーション
 *   <li>APPROVE: 承認者(部長 / CFO 等)による意思決定ステップ
 *   <li>NOTIFY: 関係者通知(取引先 / 担当営業)
 * </ul>
 *
 * <p>このフローは choreography で書くと「承認待ち」を各サービスが推測する地獄になるため orchestration 側で書く(ADR-0015 Q4「承認 /
 * 手動介入」に該当)。
 *
 * <p>SLA は **24 時間** 固定。 承認ステップが営業時間 1 日に収まらないと意思決定の優先度が低い案件と判断し、 自動 FAILED に遷移させて担当者に escalate
 * する想定。 値は将来 tenant 別 / definition 別の DB driven な設定にする予定だが、 MVP は code で固定。
 */
@Component
public class ApprovalFlowDefinition implements WorkflowDefinition {

    @Override
    public DefinitionKey key() {
        return DefinitionKey.APPROVAL_FLOW;
    }

    @Override
    public List<String> stepNames() {
        return List.of("VALIDATE", "APPROVE", "NOTIFY");
    }

    @Override
    public Duration instanceSla() {
        return Duration.ofHours(24);
    }
}
