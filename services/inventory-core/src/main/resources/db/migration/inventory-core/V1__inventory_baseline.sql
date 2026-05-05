-- Inventory Core 初期マイグレーション。
--
-- 本マイグレーションは現在の search_path に対して実行される。
-- ADR-0003(Bridge方式マルチテナンシ)に基づき、マイグレーションランナーが実行前に
-- search_path をテナントスキーマへ切替える。テーブルは tenant_<id> 配下に作成される。
-- 各テナントスキーマに対して同一スクリプトが実行される。

CREATE TABLE inventory (
    id              BIGINT       NOT NULL PRIMARY KEY,        -- Snowflake (ADR-0011)
    sku_id          VARCHAR(64)  NOT NULL,
    location_id     VARCHAR(64)  NOT NULL,
    available       INTEGER      NOT NULL CHECK (available >= 0),
    reserved        INTEGER      NOT NULL CHECK (reserved  >= 0),
    version         BIGINT       NOT NULL DEFAULT 0,
    UNIQUE (sku_id, location_id)
);

CREATE INDEX inventory_sku_idx      ON inventory (sku_id);
CREATE INDEX inventory_location_idx ON inventory (location_id);

-- Transactional Outbox(ADR-0009)。
-- ドメインイベントは集約の保存と同一トランザクションで append される。
-- 別途スケジュール起動の publisher が published=false の行をドレインして Kafka に発行する。
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
