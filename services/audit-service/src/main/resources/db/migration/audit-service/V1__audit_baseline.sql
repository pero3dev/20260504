-- Audit Service 初期マイグレーション(ADR-0008)。
--
-- マルチテナンシ方式は Pool(共通DB、tenant_id 列、ADR-0003)。
-- 本テーブルは「INSERT のみ許可」を運用ロールで強制する想定:
--   GRANT SELECT, INSERT ON audit_record TO audit_service_app;
--   -- UPDATE / DELETE は付与しない
-- 改竄防止の補完策として、Aurora WAL の別アカウント保管も併用する(ADR-0008)。

CREATE TABLE audit_record (
    tenant_id            VARCHAR(32)  NOT NULL,
    sequence             BIGINT       NOT NULL,                    -- テナント内連番(1始まり)
    event_id             BIGINT       NOT NULL UNIQUE,             -- 重複検出用
    action               VARCHAR(64)  NOT NULL,
    target_type          VARCHAR(64)  NOT NULL,
    target_id            VARCHAR(128),
    operator_user_id     VARCHAR(64),
    operator_tenant_id   VARCHAR(32),
    outcome              VARCHAR(32)  NOT NULL,
    error_code           VARCHAR(64),
    read_only            BOOLEAN      NOT NULL,
    payload_json         JSONB        NOT NULL,
    occurred_at          TIMESTAMPTZ  NOT NULL,
    prev_hash            CHAR(64)     NOT NULL,                    -- 64hex 固定
    hash                 CHAR(64)     NOT NULL,
    ingested_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, sequence)
);

CREATE INDEX audit_record_event_id_idx     ON audit_record (event_id);
CREATE INDEX audit_record_action_time_idx  ON audit_record (tenant_id, action, occurred_at);
CREATE INDEX audit_record_occurred_idx     ON audit_record (occurred_at);
