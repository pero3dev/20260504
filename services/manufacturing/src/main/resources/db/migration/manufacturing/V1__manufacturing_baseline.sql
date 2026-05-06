-- Manufacturing 初期マイグレーション。Bridge 方式(ADR-0003)。
-- テナントスキーマ配下に作成される。

-- BOM(Bill of Materials)。製品 SKU 1 単位を作るのに必要な部品 SKU と数量。
-- 1 製品 SKU は複数の構成要素を持つので、product_sku_code をキーにした 1:N の構造。
-- 階層 BOM(部品の更に下に部品...)は MVP では持たない(フラット 1 階層)。
-- 改訂版管理(version)は将来課題。本テーブルは「現行 BOM」しか持たない。
CREATE TABLE bom_component (
    product_sku_code   VARCHAR(64) NOT NULL,
    component_sku_code VARCHAR(64) NOT NULL,
    quantity_per_unit  INTEGER     NOT NULL CHECK (quantity_per_unit > 0),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (product_sku_code, component_sku_code)
);

CREATE INDEX bom_component_component_idx ON bom_component (component_sku_code);

-- 製造指図(WorkOrder 集約ルート)。
CREATE TABLE work_order (
    id                  BIGINT       NOT NULL PRIMARY KEY,        -- Snowflake (ADR-0011)
    code                VARCHAR(64)  NOT NULL UNIQUE,             -- 指図コード(MES / 帳票で参照、テナント内一意)
    product_sku_code    VARCHAR(64)  NOT NULL,                    -- 完成品 SKU
    location_id         VARCHAR(64)  NOT NULL,                    -- 製造拠点(完成品入庫先 / 部品引当元)
    planned_quantity    INTEGER      NOT NULL CHECK (planned_quantity > 0),
    status              VARCHAR(32)  NOT NULL,                    -- PLANNED / RELEASED / COMPLETED / CANCELLED
    planned_start_date  DATE,                                     -- 着手予定日(任意)
    version             BIGINT       NOT NULL DEFAULT 0,
    placed_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    released_at         TIMESTAMPTZ,                              -- release 時に埋まる
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- 指図構成要素(WorkOrder 集約の Value Object)。BOM スナップショット。
-- BOM 改訂後でも指図側は当時の構成を保持する。
CREATE TABLE work_order_component (
    work_order_id       BIGINT       NOT NULL REFERENCES work_order(id) ON DELETE CASCADE,
    line_no             INTEGER      NOT NULL,                    -- 集約内連番(1始まり)
    component_sku_code  VARCHAR(64)  NOT NULL,
    quantity_per_unit   INTEGER      NOT NULL CHECK (quantity_per_unit > 0),
    PRIMARY KEY (work_order_id, line_no)
);

CREATE INDEX work_order_status_idx       ON work_order (status);
CREATE INDEX work_order_product_idx      ON work_order (product_sku_code);
CREATE INDEX work_order_placed_idx       ON work_order (placed_at DESC);
CREATE INDEX work_order_component_sku_idx ON work_order_component (component_sku_code);

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
