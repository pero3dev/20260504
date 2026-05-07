package com.example.inventory.workflow.adapter.in.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import com.example.inventory.commons.error.BusinessException;
import com.example.inventory.workflow.application.port.in.HandleApprovalActionUseCase;
import com.example.inventory.workflow.domain.model.ApprovalAction;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * {@code workflow.approval.action.v1} を購読し、 承認アクションを workflow に流すリスナー(A1、 ADR-0015 follow-up)。
 *
 * <p>1 件失敗が他に波及しないよう、 業務例外({@link BusinessException} = 既終端 / NotFound 等)は 観察ログのみで ack
 * 続行(再投入は外部側責任)。 想定外 RuntimeException は ack せず再配信に委ねる。
 */
@Component
public class ApprovalActionListener {

    private static final Logger LOG = LoggerFactory.getLogger(ApprovalActionListener.class);

    private final HandleApprovalActionUseCase useCase;
    private final ObjectMapper objectMapper;

    public ApprovalActionListener(HandleApprovalActionUseCase useCase, ObjectMapper objectMapper) {
        this.useCase = useCase;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "workflow.approval.action.v1",
            groupId = "workflow-approval-action",
            concurrency = "1")
    public void onMessage(String payload, Acknowledgment ack) {
        ApprovalActionMessage msg;
        try {
            msg = objectMapper.readValue(payload, ApprovalActionMessage.class);
        } catch (JsonProcessingException e) {
            LOG.warn("approval action の JSON parse 失敗 — skip(再配信不能): {}", e.toString());
            ack.acknowledge();
            return;
        }

        ApprovalAction action;
        try {
            action = ApprovalAction.valueOf(msg.action());
        } catch (IllegalArgumentException e) {
            LOG.warn("approval action の action 値が不正 action={} — skip", msg.action());
            ack.acknowledge();
            return;
        }

        try {
            useCase.handle(
                    new HandleApprovalActionUseCase.Command(
                            msg.workflowId(), action, msg.actor(), msg.comment()));
            ack.acknowledge();
        } catch (BusinessException e) {
            // not-found / state-conflict は再配信しても結果が変わらない → ack
            LOG.warn(
                    "approval action 適用不可(skip)workflowId={} action={}: {}",
                    msg.workflowId(),
                    action,
                    e.toString());
            ack.acknowledge();
        }
        // RuntimeException(DB 落ち等)は ack せず Kafka 側 retry に委ねる
    }
}
