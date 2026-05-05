-- Location マスタテーブル。テナント内で一意の拠点コードを持つ。
-- Bridge 方式のためテナントスキーマ配下に作成される。

CREATE TABLE location (
    id              BIGINT       NOT NULL PRIMARY KEY,        -- Snowflake (ADR-0011)
    code            VARCHAR(64)  NOT NULL UNIQUE,             -- テナント内で一意の拠点コード
    name            VARCHAR(200) NOT NULL,
    address_line    VARCHAR(500) NOT NULL DEFAULT '',
    location_type   VARCHAR(32)  NOT NULL DEFAULT '',         -- WAREHOUSE / STORE / FACTORY / DC 等(自由文字列)
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
