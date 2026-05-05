package com.example.inventory.notification.application.port.out;

/**
 * メール送信ポート。MVP の実装は stdout 出力のみ(LoggingEmailSender)。 本番では SES / SendGrid 等に差し替える。
 *
 * <p>送信失敗は {@link EmailDeliveryException} で通知する。本サービスはリトライを内部で抱え込まず、 Kafka リスナー側の DLQ / 再配信に委ねる。
 */
public interface EmailSender {

    void send(String tenantId, String recipient, String subject, String body)
            throws EmailDeliveryException;
}
