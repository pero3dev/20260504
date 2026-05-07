package com.example.inventory.workflow.application.usecase;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.inventory.workflow.application.port.in.AdvanceWorkflowUseCase;
import com.example.inventory.workflow.application.port.in.HandleApprovalActionUseCase;
import com.example.inventory.workflow.domain.model.ApprovalAction;
import com.example.inventory.workflow.domain.model.WorkflowInstance;

/**
 * 承認アクションを既存 {@link AdvanceWorkflowUseCase} の API にディスパッチする(A1、 ADR-0015 follow-up)。
 *
 * <p>本サービスは TX を持たず、 advance 側の {@code @Transactional} に乗る。 audit / outbox は advance 側の
 * {@code @Auditable} + 集約イベントで担保される(本サービス独自の記録は無い)。
 *
 * <ul>
 *   <li>{@link ApprovalAction#APPROVE} → {@code completeCurrent}
 *   <li>{@link ApprovalAction#SKIP} → {@code completeCurrent}(REJECT と区別したいケースのため enum は分けるが、 step
 *       進行ロジックは同じ。 audit ログで識別可能)
 *   <li>{@link ApprovalAction#REJECT} → {@code failCurrent}(reason に actor + comment を載せる)
 * </ul>
 */
@Service
public class HandleApprovalActionService implements HandleApprovalActionUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(HandleApprovalActionService.class);

    private final AdvanceWorkflowUseCase advance;

    public HandleApprovalActionService(AdvanceWorkflowUseCase advance) {
        this.advance = advance;
    }

    @Override
    public WorkflowInstance handle(Command command) {
        LOG.info(
                "approval action 受信 workflowId={} action={} actor={}",
                command.workflowId(),
                command.action(),
                command.actor());
        return switch (command.action()) {
            case APPROVE, SKIP -> advance.completeCurrent(command.workflowId());
            case REJECT -> advance.failCurrent(command.workflowId(), buildRejectReason(command));
        };
    }

    private static String buildRejectReason(Command c) {
        StringBuilder sb = new StringBuilder("rejected by ").append(c.actor());
        if (c.comment() != null && !c.comment().isBlank()) {
            sb.append(": ").append(c.comment());
        }
        return sb.toString();
    }
}
