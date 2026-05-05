-- 注文明細に出荷元拠点(location_id)を追加。
-- Inventory Core が retail.order.placed.v1 を消費して在庫レコードを (sku_code, location_id) で
-- 解決するために必要(Saga 連結 Phase 1)。

ALTER TABLE retail_order_item
    ADD COLUMN location_id VARCHAR(64) NOT NULL DEFAULT '';

-- 既存行(MVP では存在しない想定だが安全のため)は default で埋める。
-- DEFAULT は新規挿入では使わず、Java 側で必須項目として渡す運用。
