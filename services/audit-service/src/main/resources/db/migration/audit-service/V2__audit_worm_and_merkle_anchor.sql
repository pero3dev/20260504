-- Audit Service WORM 強化 + Merkle anchor テーブル(ADR-0008、D3 タスク)。
--
-- 1. audit_record / audit_merkle_anchor の UPDATE/DELETE をトリガで拒否(defense-in-depth)。
--    V1 では GRANT で INSERT のみ許可するコメントだったが、
--    DBA 操作 / superuser バイパスでも痕跡が残るよう、トリガでも拒否する。
--    superuser がトリガを DROP すれば回避はできるが、
--    その操作自体が Aurora WAL に残り別経路の監査対象になる(ADR-0008)。
-- 2. 日次 Merkle anchor 保管用テーブルを追加。
--    本番では加えて S3 (Object Lock Compliance mode) に二重保管する想定だが、
--    インフラ側の別タスクで対応する。

-- ---- (1) WORM トリガ ----
CREATE OR REPLACE FUNCTION audit_record_immutable() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_record は WORM(append-only)です。% は許可されていません', TG_OP
        USING ERRCODE = 'P0001';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER audit_record_no_update
    BEFORE UPDATE ON audit_record
    FOR EACH ROW EXECUTE FUNCTION audit_record_immutable();

CREATE TRIGGER audit_record_no_delete
    BEFORE DELETE ON audit_record
    FOR EACH ROW EXECUTE FUNCTION audit_record_immutable();

-- ---- (2) Merkle anchor テーブル ----
-- テナント × 日付単位で当日の audit_record の Merkle root を保管する。
-- 改ざん検知の第二層: チェーン整合性(prev_hash 連鎖)+ anchor 整合性(Merkle root 再計算)。
CREATE TABLE audit_merkle_anchor (
    tenant_id      VARCHAR(32)  NOT NULL,
    anchor_date    DATE         NOT NULL,                -- UTC 基準
    root_hash      CHAR(64)     NOT NULL,                -- Merkle root(全レコード hash の Merkle tree)
    record_count   BIGINT       NOT NULL CHECK (record_count >= 0),
    first_sequence BIGINT       NOT NULL,                -- 期間内 sequence 範囲の下限(0=空期間)
    last_sequence  BIGINT       NOT NULL,                -- 期間内 sequence 範囲の上限(0=空期間)
    computed_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, anchor_date)
);

CREATE INDEX audit_merkle_anchor_date_idx ON audit_merkle_anchor (anchor_date DESC);

-- audit_merkle_anchor も WORM(改ざん不可)。
CREATE OR REPLACE FUNCTION audit_merkle_anchor_immutable() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_merkle_anchor は WORM(append-only)です。% は許可されていません', TG_OP
        USING ERRCODE = 'P0001';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER audit_merkle_anchor_no_update
    BEFORE UPDATE ON audit_merkle_anchor
    FOR EACH ROW EXECUTE FUNCTION audit_merkle_anchor_immutable();

CREATE TRIGGER audit_merkle_anchor_no_delete
    BEFORE DELETE ON audit_merkle_anchor
    FOR EACH ROW EXECUTE FUNCTION audit_merkle_anchor_immutable();
