package com.example.inventory.workflow.application.port.in;

import com.example.inventory.workflow.domain.model.ApprovalAction;
import com.example.inventory.workflow.domain.model.WorkflowInstance;

/**
 * 承認アクション(APPROVE / REJECT / SKIP)を受けて該当 workflow の step を進めるユースケース(A1、 ADR-0015 follow-up)。
 *
 * <p>Kafka topic {@code workflow.approval.action.v1} 経由で外部承認システム / UI から発火。 既存 {@link
 * AdvanceWorkflowUseCase} を内部で呼び、 audit + outbox の経路は変えない。
 */
public interface HandleApprovalActionUseCase {

    record Command(long workflowId, ApprovalAction action, String actor, String comment) {}

    /**
     * @return 進行後の WorkflowInstance
     * @throws WorkflowNotFoundException 該当 workflow が無い
     * @throws WorkflowStateConflictException 既に終端状態(完了/失敗/取消)等で advance 不可
     */
    WorkflowInstance handle(Command command);
}
