-- Retail/EC 出荷確定フロー(L2)用マイグレーション。
-- ADR-0017 に従い、Retail/EC 注文は Reserve のみ → 出荷確定で ship 呼出の 2 段で完結する。
-- shipped_at は出荷確定時に埋まる(NULL = 未出荷)。
-- 既存行は status='PLACED' のはずで shipped_at は NULL のまま許容。

ALTER TABLE retail_order
    ADD COLUMN shipped_at TIMESTAMPTZ;

-- SHIPPED ステータスを高速検索する索引(出荷済注文の集計、SLA レポート等を想定)。
CREATE INDEX retail_order_shipped_idx ON retail_order (shipped_at DESC) WHERE shipped_at IS NOT NULL;
