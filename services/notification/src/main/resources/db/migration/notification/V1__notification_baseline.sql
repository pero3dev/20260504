-- Notification Service 初期マイグレーション。Pool 方式(ADR-0003): tenant_id 列で分離。
-- 通知の履歴を append-only で記録する。本番運用では tenant_id を含む RLS ポリシーを別途追加する想定。

CREATE TABLE notification_record (
    id              BIGINT       NOT NULL PRIMARY KEY,        -- Snowflake (ADR-0011)
    tenant_id       VARCHAR(32)  NOT NULL,
    channel         VARCHAR(32)  NOT NULL,                    -- EMAIL / SLACK / WEBHOOK 等(MVP は EMAIL のみ)
    recipient       VARCHAR(256) NOT NULL,                    -- メールアドレス等
    subject         VARCHAR(500) NOT NULL,
    body            TEXT         NOT NULL,
    status          VARCHAR(32)  NOT NULL,                    -- SENT / FAILED
    error_message   VARCHAR(1000),
    triggered_by    VARCHAR(128) NOT NULL,                    -- 例: inventory.movement.v1
    triggered_event_id BIGINT,                                -- ソースイベントの id(冪等チェックに使用)
    occurred_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX notification_record_tenant_time_idx
    ON notification_record (tenant_id, occurred_at DESC);

CREATE INDEX notification_record_event_idx
    ON notification_record (triggered_event_id) WHERE triggered_event_id IS NOT NULL;
