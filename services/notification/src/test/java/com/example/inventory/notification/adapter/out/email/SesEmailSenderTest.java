package com.example.inventory.notification.adapter.out.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import com.example.inventory.notification.application.port.out.EmailDeliveryException;

import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.MessageRejectedException;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;
import software.amazon.awssdk.services.ses.model.SendEmailResponse;

class SesEmailSenderTest {

    private SesClient ses;
    private SesEmailSender sender;

    @BeforeEach
    void setUp() {
        ses = Mockito.mock(SesClient.class);
        sender = new SesEmailSender(ses, "noreply@example.com");
    }

    @Test
    void send_は_SES_に_SendEmailRequest_を渡し_from_to_subject_body_charset_を載せる() {
        when(ses.sendEmail(Mockito.any(SendEmailRequest.class)))
                .thenReturn(SendEmailResponse.builder().messageId("ses-msg-1").build());

        sender.send("tenant-1", "user@example.com", "件名", "本文 こんにちは");

        ArgumentCaptor<SendEmailRequest> captor = ArgumentCaptor.forClass(SendEmailRequest.class);
        verify(ses).sendEmail(captor.capture());
        SendEmailRequest req = captor.getValue();
        assertThat(req.source()).isEqualTo("noreply@example.com");
        assertThat(req.destination().toAddresses()).containsExactly("user@example.com");
        assertThat(req.message().subject().data()).isEqualTo("件名");
        assertThat(req.message().subject().charset()).isEqualTo("UTF-8");
        assertThat(req.message().body().text().data()).isEqualTo("本文 こんにちは");
        assertThat(req.message().body().text().charset()).isEqualTo("UTF-8");
    }

    @Test
    void SES_の_SesException_は_EmailDeliveryException_に包んで上位へ() {
        when(ses.sendEmail(Mockito.any(SendEmailRequest.class)))
                .thenThrow(MessageRejectedException.builder().message("rejected").build());

        assertThatThrownBy(() -> sender.send("tenant-1", "user@example.com", "s", "b"))
                .isInstanceOf(EmailDeliveryException.class)
                .hasMessageContaining("SES sendEmail 失敗")
                .hasCauseInstanceOf(MessageRejectedException.class);
    }
}
