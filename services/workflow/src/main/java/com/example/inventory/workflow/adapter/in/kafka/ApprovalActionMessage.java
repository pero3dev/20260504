package com.example.inventory.workflow.adapter.in.kafka;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * {@code workflow.approval.action.v1} の入力メッセージ DTO(A1、 ADR-0015 follow-up)。
 *
 * <p>外部承認システム / UI が Kafka に publish する。 {@code workflowId} は workflow service が払い出した Snowflake、
 * {@code action} は {@code APPROVE | REJECT | SKIP}。
 */
public record ApprovalActionMessage(long workflowId, String action, String actor, String comment) {

    @JsonCreator
    public ApprovalActionMessage(
            @JsonProperty("workflowId") long workflowId,
            @JsonProperty("action") String action,
            @JsonProperty("actor") String actor,
            @JsonProperty("comment") String comment) {
        this.workflowId = workflowId;
        this.action = action;
        this.actor = actor;
        this.comment = comment;
    }
}
