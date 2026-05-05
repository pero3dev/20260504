-- Partner マスタテーブル。テナント内で一意の取引先コードを持つ。
-- Bridge 方式のためテナントスキーマ配下に作成される。

CREATE TABLE partner (
    id              BIGINT       NOT NULL PRIMARY KEY,        -- Snowflake (ADR-0011)
    code            VARCHAR(64)  NOT NULL UNIQUE,             -- テナント内で一意の取引先コード
    name            VARCHAR(200) NOT NULL,
    partner_type    VARCHAR(32)  NOT NULL DEFAULT '',         -- CUSTOMER / SUPPLIER / CARRIER 等(自由文字列)
    contact_email   VARCHAR(200) NOT NULL DEFAULT '',
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
