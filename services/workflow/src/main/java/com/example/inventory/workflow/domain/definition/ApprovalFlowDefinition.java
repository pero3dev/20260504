package com.example.inventory.workflow.domain.definition;

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
}
