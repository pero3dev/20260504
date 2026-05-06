-- Workflow Service 初期マイグレーション(ADR-0015 / G タスク)。
-- Pool 方式マルチテナンシ(共通DB、tenant_id 列、ADR-0003)。

-- ワークフロー実行インスタンス。
-- definition_key は静的定義の識別子(MVP は ApprovalFlow 等を enum で固定)。
-- payload は各ステップが参照するメタデータ(注文 ID / 取引先コード等)を JSON で持つ。
CREATE TABLE workflow_instance (
    id              BIGINT       NOT NULL PRIMARY KEY,        -- Snowflake (ADR-0011)
    tenant_id       VARCHAR(32)  NOT NULL,
    definition_key  VARCHAR(64)  NOT NULL,                    -- APPROVAL_FLOW 等
    business_key    VARCHAR(128),                             -- 業務側の参照キー(注文コード 等、検索用)
    payload_json    JSONB        NOT NULL,
    current_step    INTEGER      NOT NULL,                    -- 1 始まり
    total_steps     INTEGER      NOT NULL,                    -- 定義スナップショット
    status          VARCHAR(32)  NOT NULL,                    -- STARTED / COMPLETED / FAILED / CANCELLED
    error           TEXT,                                     -- 失敗時の理由
    version         BIGINT       NOT NULL DEFAULT 0,
    started_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    completed_at    TIMESTAMPTZ,
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX workflow_instance_tenant_idx     ON workflow_instance (tenant_id, status);
CREATE INDEX workflow_instance_business_idx   ON workflow_instance (tenant_id, business_key);
CREATE INDEX workflow_instance_def_status_idx ON workflow_instance (definition_key, status);

-- ステップ実行履歴。集約境界内の Value Object コレクション。
CREATE TABLE workflow_step (
    instance_id     BIGINT       NOT NULL REFERENCES workflow_instance(id) ON DELETE CASCADE,
    step_no         INTEGER      NOT NULL,                    -- 1 始まり
    name            VARCHAR(64)  NOT NULL,                    -- VALIDATE / APPROVE / NOTIFY 等
    status          VARCHAR(32)  NOT NULL,                    -- PENDING / RUNNING / COMPLETED / FAILED / SKIPPED
    started_at      TIMESTAMPTZ,                              -- RUNNING に遷移したとき埋まる
    completed_at    TIMESTAMPTZ,                              -- COMPLETED / FAILED で埋まる
    error           TEXT,
    PRIMARY KEY (instance_id, step_no)
);

CREATE INDEX workflow_step_status_idx ON workflow_step (status);

-- Transactional Outbox(ADR-0009)。インスタンス完了時に発行。
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
