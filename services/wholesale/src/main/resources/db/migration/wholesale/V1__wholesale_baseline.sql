-- Wholesale 初期マイグレーション。Bridge 方式(ADR-0003)。
-- テナントスキーマ配下に作成される。

-- 取引先別契約価格マスタ。
-- Wholesale 業態の本質は「同じ SKU でも取引先によって単価が違う」こと。
-- 受注時に (partner_code, sku_code) で参照し line_unit_price を埋める。
-- price_tier は将来の階層別価格(数量・期間)拡張のためのプレースホルダ(MVP は STANDARD のみ)。
CREATE TABLE partner_price (
    partner_code    VARCHAR(64)  NOT NULL,
    sku_code        VARCHAR(64)  NOT NULL,
    price_tier      VARCHAR(32)  NOT NULL DEFAULT 'STANDARD',
    unit_price      NUMERIC(15,2) NOT NULL CHECK (unit_price >= 0),
    currency        VARCHAR(3)   NOT NULL DEFAULT 'JPY',
    valid_from      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    valid_to        TIMESTAMPTZ,                              -- NULL = 現行有効
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (partner_code, sku_code, price_tier)
);

CREATE INDEX partner_price_sku_idx ON partner_price (sku_code);

-- 受注ヘッダ(SalesOrder 集約ルート)。
CREATE TABLE sales_order (
    id              BIGINT       NOT NULL PRIMARY KEY,        -- Snowflake (ADR-0011)
    code            VARCHAR(64)  NOT NULL UNIQUE,             -- 受注コード(顧客向け表示、テナント内一意)
    partner_code    VARCHAR(64)  NOT NULL,                    -- 取引先(master-data の Partner と同 ID)
    status          VARCHAR(32)  NOT NULL,                    -- PLACED / CANCELLED
    total_amount    NUMERIC(15,2) NOT NULL,
    currency        VARCHAR(3)   NOT NULL DEFAULT 'JPY',
    requested_delivery_date  DATE,                            -- 希望納期(任意)
    version         BIGINT       NOT NULL DEFAULT 0,
    placed_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- 受注明細(SalesOrder 集約の Value Object)。
CREATE TABLE sales_order_item (
    order_id        BIGINT       NOT NULL REFERENCES sales_order(id) ON DELETE CASCADE,
    line_no         INTEGER      NOT NULL,                    -- 集約内連番(1始まり)
    sku_code        VARCHAR(64)  NOT NULL,
    location_id     VARCHAR(64)  NOT NULL,                    -- 出荷元拠点
    quantity        INTEGER      NOT NULL CHECK (quantity > 0),
    unit_price      NUMERIC(15,2) NOT NULL,                   -- 受注時 partner_price スナップショット
    PRIMARY KEY (order_id, line_no)
);

CREATE INDEX sales_order_partner_idx ON sales_order (partner_code);
CREATE INDEX sales_order_status_idx  ON sales_order (status);
CREATE INDEX sales_order_placed_idx  ON sales_order (placed_at DESC);
CREATE INDEX sales_order_item_sku_idx ON sales_order_item (sku_code);

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
