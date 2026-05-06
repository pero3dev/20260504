package com.example.inventory.analytics.application.port.out;

import com.example.inventory.commons.tenant.TenantId;

/**
 * 重複イベント検出用のポート。Outbox の event_id を一意に保持し、Kafka 再配信での 二重集計を防ぐ。
 *
 * <p>{@link #markProcessed} は INSERT。同 event_id が既存なら DuplicateKeyException が起きる前提で
 * 呼出側が拾って冪等スキップする。
 */
public interface ProcessedEventRepository {

    boolean exists(long eventId);

    void markProcessed(long eventId, TenantId tenantId, String topic);
}
