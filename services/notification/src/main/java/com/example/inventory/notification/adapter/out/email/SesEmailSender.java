package com.example.inventory.notification.adapter.out.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.inventory.notification.application.port.out.EmailDeliveryException;
import com.example.inventory.notification.application.port.out.EmailSender;

import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.Body;
import software.amazon.awssdk.services.ses.model.Content;
import software.amazon.awssdk.services.ses.model.Destination;
import software.amazon.awssdk.services.ses.model.Message;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;
import software.amazon.awssdk.services.ses.model.SesException;

/**
 * AWS SES 経由のメール送信実装。
 *
 * <p>{@code notification.email.provider=ses} で Bean 化される。 from address は設定で指定(SES verified identity
 * が必要、 production sandbox 解除も)。 charset は固定 UTF-8。
 *
 * <p>SES 側の throttling / 失敗は {@link EmailDeliveryException} に包んで上位へ。 リトライは Kafka リスナー側の DLQ /
 * 再配信に委ねる(本クラスは内部リトライ無し)。
 *
 * <p>tag 付け / configuration set 等の高度な機能は MVP 範囲外。
 */
public class SesEmailSender implements EmailSender {

    private static final Logger LOG = LoggerFactory.getLogger(SesEmailSender.class);
    private static final String CHARSET = "UTF-8";

    private final SesClient ses;
    private final String fromAddress;

    public SesEmailSender(SesClient ses, String fromAddress) {
        this.ses = ses;
        this.fromAddress = fromAddress;
    }

    @Override
    public void send(String tenantId, String recipient, String subject, String body)
            throws EmailDeliveryException {
        SendEmailRequest req =
                SendEmailRequest.builder()
                        .source(fromAddress)
                        .destination(Destination.builder().toAddresses(recipient).build())
                        .message(
                                Message.builder()
                                        .subject(content(subject))
                                        .body(Body.builder().text(content(body)).build())
                                        .build())
                        .build();
        try {
            String messageId = ses.sendEmail(req).messageId();
            LOG.info(
                    "[EMAIL/SES] tenant={} to={} subject={} messageId={}",
                    tenantId,
                    recipient,
                    subject,
                    messageId);
        } catch (SesException e) {
            throw new EmailDeliveryException(
                    "SES sendEmail 失敗 tenant=" + tenantId + " to=" + recipient, e);
        }
    }

    private static Content content(String value) {
        return Content.builder().data(value).charset(CHARSET).build();
    }
}
