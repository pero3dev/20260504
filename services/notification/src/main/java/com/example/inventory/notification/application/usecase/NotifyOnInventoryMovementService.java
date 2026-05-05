package com.example.inventory.notification.application.usecase;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.inventory.commons.persistence.SnowflakeIdGenerator;
import com.example.inventory.notification.application.port.in.NotifyOnInventoryMovementUseCase;
import com.example.inventory.notification.application.port.out.EmailDeliveryException;
import com.example.inventory.notification.application.port.out.EmailSender;
import com.example.inventory.notification.application.port.out.NotificationRepository;
import com.example.inventory.notification.domain.model.Notification;
import com.example.inventory.notification.domain.model.NotificationChannel;

@Service
public class NotifyOnInventoryMovementService implements NotifyOnInventoryMovementUseCase {

    private static final Logger LOG =
            LoggerFactory.getLogger(NotifyOnInventoryMovementService.class);

    /** MVP 用の固定送信先。今後 Subscription 機能で tenant ごとの宛先管理に置換する。 */
    private static final String DEFAULT_RECIPIENT = "ops@example.com";

    private static final String TRIGGERED_BY = "inventory.movement.v1";

    private final NotificationRepository repository;
    private final EmailSender emailSender;
    private final SnowflakeIdGenerator idGenerator;
    private final int lowStockThreshold;

    public NotifyOnInventoryMovementService(
            NotificationRepository repository,
            EmailSender emailSender,
            SnowflakeIdGenerator idGenerator,
            @Value("${notification.inventory.low-stock-threshold:5}") int lowStockThreshold) {
        this.repository = repository;
        this.emailSender = emailSender;
        this.idGenerator = idGenerator;
        this.lowStockThreshold = lowStockThreshold;
    }

    @Override
    @Transactional
    public void notifyIfNeeded(Command command) {
        // 閾値以下でなければ通知不要
        if (command.availableAfter() > lowStockThreshold) {
            return;
        }

        // 同一イベントからの重複送信を抑止(at-least-once 配信に対する冪等)
        if (repository.existsByTriggeredEventId(command.eventId())) {
            LOG.debug("既に通知済み eventId={}", command.eventId());
            return;
        }

        long id = idGenerator.nextId();
        String subject = buildSubject(command);
        String body = buildBody(command);

        Notification record;
        try {
            emailSender.send(command.tenantId(), DEFAULT_RECIPIENT, subject, body);
            record =
                    Notification.sent(
                            id,
                            command.tenantId(),
                            NotificationChannel.EMAIL,
                            DEFAULT_RECIPIENT,
                            subject,
                            body,
                            TRIGGERED_BY,
                            command.eventId());
        } catch (EmailDeliveryException e) {
            LOG.warn(
                    "メール送信失敗 tenant={} eventId={}: {}",
                    command.tenantId(),
                    command.eventId(),
                    e.toString());
            record =
                    Notification.failed(
                            id,
                            command.tenantId(),
                            NotificationChannel.EMAIL,
                            DEFAULT_RECIPIENT,
                            subject,
                            body,
                            e.toString(),
                            TRIGGERED_BY,
                            command.eventId());
        }
        repository.append(record);
    }

    private static String buildSubject(Command c) {
        return "[在庫低下] " + c.skuId() + " @ " + c.locationId();
    }

    private static String buildBody(Command c) {
        return String.format(
                "テナント %s の在庫が閾値を下回りました。%n"
                        + "  SKU: %s%n"
                        + "  Location: %s%n"
                        + "  Available: %d%n"
                        + "  Reserved: %d%n"
                        + "  Inventory ID: %d%n",
                c.tenantId(),
                c.skuId(),
                c.locationId(),
                c.availableAfter(),
                c.reservedAfter(),
                c.inventoryId());
    }
}
