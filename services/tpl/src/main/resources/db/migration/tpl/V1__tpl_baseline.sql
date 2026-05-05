-- 3PL 初期マイグレーション。Bridge 方式(ADR-0003)。
-- テナントスキーマ配下に作成される。

-- 入出庫1件を表す。Partner(委託元)ごと、(SKU, Location) ごとに movement_type と quantity を記録。
CREATE TABLE stock_movement (
    id              BIGINT       NOT NULL PRIMARY KEY,        -- Snowflake (ADR-0011)
    code            VARCHAR(64)  NOT NULL UNIQUE,             -- 入出庫コード(自社内一意、ASN/出荷指示書番号 等で参照される)
    partner_code    VARCHAR(64)  NOT NULL,                    -- 委託元(master-data の Partner と紐付く)
    sku_code        VARCHAR(64)  NOT NULL,
    location_id     VARCHAR(64)  NOT NULL,                    -- 自社倉庫
    movement_type   VARCHAR(16)  NOT NULL,                    -- INBOUND / OUTBOUND / ADJUSTMENT
    quantity        INTEGER      NOT NULL CHECK (quantity > 0),
    status          VARCHAR(32)  NOT NULL,                    -- PLANNED / RECEIVED / DISPATCHED / CANCELLED
    reference_code  VARCHAR(128),                             -- 外部連携用(ASN番号、出荷指示番号等)
    version         BIGINT       NOT NULL DEFAULT 0,
    planned_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX stock_movement_partner_idx       ON stock_movement (partner_code, planned_at DESC);
CREATE INDEX stock_movement_sku_location_idx  ON stock_movement (sku_code, location_id);
CREATE INDEX stock_movement_status_idx        ON stock_movement (status);

-- Transactional Outbox(ADR-0009)。
CREATE TABLE outbox (
    event_id        BIGINT       NOT NULL PRIMARY KEY,
    tenant_id       VARCHAR(32)  NOT NULL,
    topic           VARCHAR(128) NOT NULL,
    schema_version  VARCHAR(16)  NOT NULL,
    aggregate_id    BIGINT       NOT NULL,
    payload         JSONB        NOT NULL,
    trace_id        VARCHAR(64),
    occurred_at     TIMESTAMPTZ  NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published       BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE INDEX outbox_unpublished_idx ON outbox (created_at) WHERE published = FALSE;
