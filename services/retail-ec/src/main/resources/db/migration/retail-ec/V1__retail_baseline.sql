-- Retail/EC 初期マイグレーション。Bridge 方式(ADR-0003)。
-- テナントスキーマ配下に作成される。

-- 注文ヘッダ。
CREATE TABLE retail_order (
    id              BIGINT       NOT NULL PRIMARY KEY,        -- Snowflake (ADR-0011)
    code            VARCHAR(64)  NOT NULL UNIQUE,             -- 注文コード(顧客向け表示用、テナント内一意)
    customer_email  VARCHAR(256) NOT NULL,
    status          VARCHAR(32)  NOT NULL,                    -- PLACED / CANCELLED
    total_amount    NUMERIC(15,2) NOT NULL,
    currency        VARCHAR(3)   NOT NULL DEFAULT 'JPY',
    version         BIGINT       NOT NULL DEFAULT 0,
    placed_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- 注文明細。Order 集約の Value Object 群。
CREATE TABLE retail_order_item (
    order_id        BIGINT       NOT NULL REFERENCES retail_order(id) ON DELETE CASCADE,
    line_no         INTEGER      NOT NULL,                    -- 集約内連番(1始まり)
    sku_code        VARCHAR(64)  NOT NULL,
    quantity        INTEGER      NOT NULL CHECK (quantity > 0),
    unit_price      NUMERIC(15,2) NOT NULL,
    PRIMARY KEY (order_id, line_no)
);

CREATE INDEX retail_order_status_idx  ON retail_order (status);
CREATE INDEX retail_order_placed_idx  ON retail_order (placed_at DESC);
CREATE INDEX retail_order_item_sku_idx ON retail_order_item (sku_code);

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
