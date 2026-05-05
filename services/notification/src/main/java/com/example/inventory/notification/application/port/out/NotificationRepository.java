package com.example.inventory.notification.application.port.out;

import com.example.inventory.notification.domain.model.Notification;

/** 通知履歴の永続化ポート。append-only(更新無し)。 */
public interface NotificationRepository {

    /** 同一の triggered_event_id ですでに記録済みなら true(冪等チェック)。 */
    boolean existsByTriggeredEventId(long triggeredEventId);

    void append(Notification notification);
}
