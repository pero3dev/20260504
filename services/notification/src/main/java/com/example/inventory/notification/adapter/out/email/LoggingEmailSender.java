package com.example.inventory.notification.adapter.out.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.inventory.notification.application.port.out.EmailSender;

/**
 * ログ出力するだけのスタブ実装(dev / loadtest / 単体テスト用)。
 *
 * <p>ログレベル INFO で 1 行 + body を続けて出す。出力されたログから「誰宛に何を送ったか」を後追いできる。
 *
 * <p>{@code notification.email.provider=logging}(default)で Bean 化される。 本番の {@code provider=ses} では
 * {@link SesEmailSender} に差し替わる。
 */
public class LoggingEmailSender implements EmailSender {

    private static final Logger LOG = LoggerFactory.getLogger(LoggingEmailSender.class);

    @Override
    public void send(String tenantId, String recipient, String subject, String body) {
        LOG.info(
                "[EMAIL/STUB] tenant={} to={} subject={}\n----- BODY -----\n{}\n--------------",
                tenantId,
                recipient,
                subject,
                body);
    }
}
