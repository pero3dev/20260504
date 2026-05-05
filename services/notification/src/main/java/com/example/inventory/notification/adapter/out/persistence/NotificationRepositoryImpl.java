package com.example.inventory.notification.adapter.out.persistence;

import org.springframework.stereotype.Repository;

import com.example.inventory.notification.application.port.out.NotificationRepository;
import com.example.inventory.notification.domain.model.Notification;

@Repository
public class NotificationRepositoryImpl implements NotificationRepository {

    private final NotificationMapper mapper;

    public NotificationRepositoryImpl(NotificationMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public boolean existsByTriggeredEventId(long triggeredEventId) {
        return mapper.existsByTriggeredEventId(triggeredEventId) > 0;
    }

    @Override
    public void append(Notification n) {
        mapper.insert(
                new NotificationRow(
                        n.id(),
                        n.tenantId(),
                        n.channel().name(),
                        n.recipient(),
                        n.subject(),
                        n.body(),
                        n.status().name(),
                        n.errorMessage(),
                        n.triggeredBy(),
                        n.triggeredEventId(),
                        n.occurredAt()));
    }
}
