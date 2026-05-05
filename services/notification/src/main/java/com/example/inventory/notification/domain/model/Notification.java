package com.example.inventory.notification.domain.model;

import java.time.Instant;

/**
 * 通知レコード(履歴用、append-only)。集約というよりは Result Object で、send 後の状態を表す。
 *
 * <p>{@code triggeredEventId} は冪等性のためのソースイベント識別子。同一イベントから重複送信しないよう、 受信ハンドラが事前にチェックする。
 */
public record Notification(
        long id,
        String tenantId,
        NotificationChannel channel,
        String recipient,
        String subject,
        String body,
        NotificationStatus status,
        String errorMessage,
        String triggeredBy,
        Long triggeredEventId,
        Instant occurredAt) {

    public Notification {
        if (id <= 0) throw new IllegalArgumentException("id は正の値");
        if (tenantId == null || tenantId.isBlank())
            throw new IllegalArgumentException("tenantId 必須");
        if (channel == null) throw new IllegalArgumentException("channel 必須");
        if (recipient == null || recipient.isBlank())
            throw new IllegalArgumentException("recipient 必須");
        if (subject == null) throw new IllegalArgumentException("subject 必須(空文字許容)");
        if (body == null) throw new IllegalArgumentException("body 必須(空文字許容)");
        if (status == null) throw new IllegalArgumentException("status 必須");
        if (occurredAt == null) throw new IllegalArgumentException("occurredAt 必須");
    }

    public static Notification sent(
            long id,
            String tenantId,
            NotificationChannel channel,
            String recipient,
            String subject,
            String body,
            String triggeredBy,
            Long triggeredEventId) {
        return new Notification(
                id,
                tenantId,
                channel,
                recipient,
                subject,
                body,
                NotificationStatus.SENT,
                null,
                triggeredBy,
                triggeredEventId,
                Instant.now());
    }

    public static Notification failed(
            long id,
            String tenantId,
            NotificationChannel channel,
            String recipient,
            String subject,
            String body,
            String error,
            String triggeredBy,
            Long triggeredEventId) {
        return new Notification(
                id,
                tenantId,
                channel,
                recipient,
                subject,
                body,
                NotificationStatus.FAILED,
                error,
                triggeredBy,
                triggeredEventId,
                Instant.now());
    }
}
