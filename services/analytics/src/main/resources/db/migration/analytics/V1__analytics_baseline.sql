-- Analytics Service 初期マイグレーション。Pool 方式(共通DB、tenant_id 列、ADR-0003)。
-- 単一データベースに全テナントのレコードが入る。tenant_id を必ず WHERE に入れる規約。

-- 重複イベント検出用の処理済み event_id 集合。
-- 業態系から流れる Outbox の event_id を一意に保持し、Kafka 再配信で同じイベントが
-- 二重に集計されることを防ぐ(idempotent ingestion)。
CREATE TABLE processed_event (
    event_id        BIGINT       NOT NULL PRIMARY KEY,
    tenant_id       VARCHAR(32)  NOT NULL,
    topic           VARCHAR(128) NOT NULL,
    processed_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX processed_event_tenant_idx ON processed_event (tenant_id, processed_at DESC);

-- テナント × 業態 × 日付 単位の注文集計テーブル(MVP)。
-- イベントを受けるたびに UPSERT で order_count と total_amount を加算する。
-- 業態は ADR-0002 の bounded context 名前空間と一致(retail / wholesale)。
CREATE TABLE daily_order_summary (
    tenant_id           VARCHAR(32)   NOT NULL,
    business_context    VARCHAR(32)   NOT NULL,           -- retail / wholesale
    summary_date        DATE          NOT NULL,           -- UTC 基準
    currency            VARCHAR(3)    NOT NULL,
    order_count         BIGINT        NOT NULL DEFAULT 0,
    total_amount        NUMERIC(18,2) NOT NULL DEFAULT 0,
    last_event_at       TIMESTAMPTZ   NOT NULL,
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, business_context, summary_date, currency)
);

CREATE INDEX daily_order_summary_date_idx ON daily_order_summary (summary_date DESC);
CREATE INDEX daily_order_summary_tenant_date_idx
    ON daily_order_summary (tenant_id, summary_date DESC);
