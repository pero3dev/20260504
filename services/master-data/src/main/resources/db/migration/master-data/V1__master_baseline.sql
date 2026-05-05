-- Master Data 初期マイグレーション。Bridge 方式(ADR-0003)。
-- 本マイグレーションはテナントスキーマに対して実行される(K8s Job が search_path を切替えてから実行)。

CREATE TABLE sku (
    id              BIGINT       NOT NULL PRIMARY KEY,        -- Snowflake (ADR-0011)
    code            VARCHAR(64)  NOT NULL UNIQUE,             -- テナント内で一意の SKU コード
    name            VARCHAR(200) NOT NULL,
    description     VARCHAR(1000) NOT NULL DEFAULT '',
    unit_of_measure VARCHAR(32)  NOT NULL DEFAULT '',
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- TODO: Location / Partner テーブルは次イテレーションで追加。

-- Transactional Outbox(ADR-0009)
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
