-- Manufacturing 完成品 INBOUND フロー(L3)用マイグレーション。
-- ADR-0017 の follow-up tasks を解消。release で部品消費 → complete で完成品 INBOUND の
-- 2 段で WorkOrder ライフサイクル全体を閉じる。
-- completed_at は完了確定時に埋まる(NULL = 未完了)。

ALTER TABLE work_order
    ADD COLUMN completed_at TIMESTAMPTZ;

-- COMPLETED ステータスを高速検索する索引(製造リードタイム集計、KPI レポート等を想定)。
CREATE INDEX work_order_completed_idx ON work_order (completed_at DESC) WHERE completed_at IS NOT NULL;
