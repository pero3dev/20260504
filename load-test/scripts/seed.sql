-- 負荷試験用 一発シード:
--   1. tenant_dev スキーマ作成(Bridge 方式マルチテナンシ ADR-0003)
--   2. inventory-core のマイグレーション DDL を inline で実行
--      (Inventory Core は spring.flyway.enabled=false 既定なので
--       本ファイルが Flyway 代替として機能する)
--   3. Inventory 1000 件、 SKU registry 1000 件を seed
--
-- 実行:
--   psql -h localhost -p 5433 -U test -d inventory_core -f load-test/scripts/seed.sql
--
-- 再実行(冪等):
--   先頭の DROP SCHEMA で初期化される。 既存データを保持したい場合は本ファイルを使わない。

\set ON_ERROR_STOP on

DROP SCHEMA IF EXISTS tenant_dev CASCADE;
CREATE SCHEMA tenant_dev;
SET search_path TO tenant_dev;

-- ============================================================
-- V1__inventory_baseline.sql 相当
-- ============================================================

CREATE TABLE inventory (
    id              BIGINT       NOT NULL PRIMARY KEY,
    sku_id          VARCHAR(64)  NOT NULL,
    location_id     VARCHAR(64)  NOT NULL,
    available       INTEGER      NOT NULL CHECK (available >= 0),
    reserved        INTEGER      NOT NULL CHECK (reserved  >= 0),
    version         BIGINT       NOT NULL DEFAULT 0,
    UNIQUE (sku_id, location_id)
);

CREATE INDEX inventory_sku_idx      ON inventory (sku_id);
CREATE INDEX inventory_location_idx ON inventory (location_id);

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

-- ============================================================
-- V2__sku_registry.sql 相当
-- ============================================================

CREATE TABLE sku_registry (
    code            VARCHAR(64)  NOT NULL PRIMARY KEY,
    aggregate_id    BIGINT       NOT NULL,
    name            VARCHAR(200) NOT NULL,
    unit_of_measure VARCHAR(32)  NOT NULL DEFAULT '',
    version         BIGINT       NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ============================================================
-- 負荷試験用シード
-- ============================================================

-- 1000 件の inventory(SKU-0001 〜 SKU-1000、 LOC-1 単一拠点)
-- 各 SKU で 100 万個用意し、 reserve が枯渇しないようにする。
INSERT INTO inventory (id, sku_id, location_id, available, reserved, version)
SELECT
    n,
    'SKU-' || LPAD(n::text, 4, '0'),
    'LOC-1',
    1000000,
    0,
    1
FROM generate_series(1, 1000) AS n;

-- 同じ SKU を sku_registry にも投入(Reserve は SKU 登録チェックを通る)
INSERT INTO sku_registry (code, aggregate_id, name, unit_of_measure, version)
SELECT
    'SKU-' || LPAD(n::text, 4, '0'),
    1000000 + n,
    'Load test SKU ' || n,
    'PCS',
    1
FROM generate_series(1, 1000) AS n;

-- 確認
SELECT count(*) AS inventory_count FROM inventory;
SELECT count(*) AS sku_count FROM sku_registry;
