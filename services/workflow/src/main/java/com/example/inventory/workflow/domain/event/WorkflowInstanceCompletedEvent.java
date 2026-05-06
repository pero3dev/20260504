package com.example.inventory.workflow.domain.event;

import java.time.Instant;

import com.example.inventory.commons.event.DomainEvent;
import com.example.inventory.workflow.domain.model.WorkflowStatus;

/**
 * ワークフローインスタンス完了イベント。{@code workflow.instance.completed.v1}。
 *
 * <p>{@code finalStatus} は COMPLETED / FAILED / CANCELLED のいずれか。 購読者は業務側サービスで「Saga が終わった → 後続処理を起動
 * / 通知を発火」のトリガに使う。
 *
 * <p>MVP は本イベントのみ発行。{@code workflow.instance.started.v1} や {@code workflow.step.completed.v1}
 * は将来の進行可視化のために追加予定だが、現状は購読側ニーズなしで割愛。
 */
public record WorkflowInstanceCompletedEvent(
        long aggregateId,
        String definitionKey,
        String businessKey,
        WorkflowStatus finalStatus,
        String error,
        Instant completedAt,
        Instant occurredAt)
        implements DomainEvent {

    public static final String TOPIC = "workflow.instance.completed.v1";
    public static final String SCHEMA_VERSION = "1.0";

    @Override
    public String topic() {
        return TOPIC;
    }

    @Override
    public String schemaVersion() {
        return SCHEMA_VERSION;
    }
}
