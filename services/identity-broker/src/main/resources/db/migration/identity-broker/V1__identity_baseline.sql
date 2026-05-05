-- Identity Broker 初期マイグレーション。
--
-- マルチテナンシ方式は Pool(ADR-0003): 単一スキーマに tenant_id 列。
-- ユーザーテーブルはテナント横断(同じユーザーが複数テナントに所属可能)。
-- メンバーシップは (user_id, tenant_id) で 1 レコード。

CREATE TABLE users (
    id              BIGINT       NOT NULL PRIMARY KEY,        -- Snowflake (ADR-0011)
    email           VARCHAR(254) NOT NULL UNIQUE,
    password_hash   VARCHAR(72)  NOT NULL,                    -- BCrypt は 60文字、余裕で 72
    display_name    VARCHAR(128) NOT NULL DEFAULT '',
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX users_email_idx ON users (email);

CREATE TABLE tenant_memberships (
    id                    BIGINT       NOT NULL PRIMARY KEY,
    user_id               BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    tenant_id             VARCHAR(32)  NOT NULL,
    tenant_display_name   VARCHAR(128) NOT NULL DEFAULT '',
    -- ロール(配列)
    roles_json            JSONB        NOT NULL DEFAULT '[]'::jsonb,
    -- データスコープ
    location_scopes_json  JSONB        NOT NULL DEFAULT '[]'::jsonb,
    partner_scopes_json   JSONB        NOT NULL DEFAULT '[]'::jsonb,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (user_id, tenant_id)
);

CREATE INDEX tenant_memberships_user_idx   ON tenant_memberships (user_id);
CREATE INDEX tenant_memberships_tenant_idx ON tenant_memberships (tenant_id);

-- Transactional Outbox(ADR-0009、共通テンプレート)
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
