package com.example.inventory.notification.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import com.example.inventory.commons.persistence.SnowflakeIdGenerator;
import com.example.inventory.notification.application.port.in.NotifyOnInventoryMovementUseCase.Command;
import com.example.inventory.notification.application.port.out.EmailDeliveryException;
import com.example.inventory.notification.application.port.out.EmailSender;
import com.example.inventory.notification.application.port.out.NotificationRepository;
import com.example.inventory.notification.domain.model.Notification;
import com.example.inventory.notification.domain.model.NotificationStatus;

class NotifyOnInventoryMovementServiceTest {

    private NotificationRepository repository;
    private EmailSender emailSender;
    private SnowflakeIdGenerator idGenerator;
    private NotifyOnInventoryMovementService service;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(NotificationRepository.class);
        emailSender = Mockito.mock(EmailSender.class);
        idGenerator = Mockito.mock(SnowflakeIdGenerator.class);
        when(idGenerator.nextId()).thenReturn(900_000_001L);
        service = new NotifyOnInventoryMovementService(repository, emailSender, idGenerator, 5);
    }

    private static Command cmd(int availableAfter, long eventId) {
        return new Command(eventId, "dev", 1L, "SKU-1", "LOC-1", availableAfter, 0, 1L);
    }

    @Test
    void 閾値より上なら通知しない() {
        service.notifyIfNeeded(cmd(10, 1L));

        verify(emailSender, never())
                .send(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
        verify(repository, never()).append(Mockito.any());
    }

    @Test
    void 閾値以下なら送信して履歴を記録する() {
        when(repository.existsByTriggeredEventId(anyLong())).thenReturn(false);

        service.notifyIfNeeded(cmd(3, 1001L));

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(repository, times(1)).append(captor.capture());
        Notification stored = captor.getValue();
        assertThat(stored.status()).isEqualTo(NotificationStatus.SENT);
        assertThat(stored.tenantId()).isEqualTo("dev");
        assertThat(stored.triggeredEventId()).isEqualTo(1001L);
        assertThat(stored.subject()).contains("SKU-1");
    }

    @Test
    void 同一eventIdは重複送信しない() {
        when(repository.existsByTriggeredEventId(1001L)).thenReturn(true);

        service.notifyIfNeeded(cmd(2, 1001L));

        verify(emailSender, never())
                .send(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
        verify(repository, never()).append(Mockito.any());
    }

    @Test
    void 送信失敗でも_FAILED_履歴を残す() {
        when(repository.existsByTriggeredEventId(anyLong())).thenReturn(false);
        doThrow(new EmailDeliveryException("SMTP timeout"))
                .when(emailSender)
                .send(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());

        service.notifyIfNeeded(cmd(0, 2002L));

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(repository, times(1)).append(captor.capture());
        Notification stored = captor.getValue();
        assertThat(stored.status()).isEqualTo(NotificationStatus.FAILED);
        assertThat(stored.errorMessage()).contains("SMTP timeout");
    }
}
