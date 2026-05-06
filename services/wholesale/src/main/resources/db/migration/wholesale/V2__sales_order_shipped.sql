-- Wholesale 出荷確定フロー(L1)用マイグレーション。
-- ADR-0017 に従い、Wholesale 受注は Reserve のみ → 出荷確定で ship 呼出の 2 段で完結する。
-- shipped_at は出荷確定時に埋まる(NULL = 未出荷)。
-- 既存行は status='PLACED' のはずで shipped_at は NULL のまま許容。

ALTER TABLE sales_order
    ADD COLUMN shipped_at TIMESTAMPTZ;

-- SHIPPED ステータスを高速検索する索引(出荷済受注の集計、SLA レポート等を想定)。
CREATE INDEX sales_order_shipped_idx ON sales_order (shipped_at DESC) WHERE shipped_at IS NOT NULL;
