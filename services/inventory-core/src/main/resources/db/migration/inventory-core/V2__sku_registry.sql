-- Master Data サービスから master.product.v1 を消費して保持する SKU 投影テーブル(ADR-0004)。
-- Bridge 方式(ADR-0003)のためテナントスキーマ配下に作成される。
-- 引当ユースケースが SKU の存在確認に利用する。本テーブルはマスタの権威ではなく、
-- あくまで結果整合な投影(キャッシュに近い扱い)。

CREATE TABLE sku_registry (
    code            VARCHAR(64)  NOT NULL PRIMARY KEY,         -- 自然キー(Inventory.sku_id と同一スコープ)
    aggregate_id    BIGINT       NOT NULL,                     -- Master Data 側 Snowflake(監査用)
    name            VARCHAR(200) NOT NULL,
    unit_of_measure VARCHAR(32)  NOT NULL DEFAULT '',
    version         BIGINT       NOT NULL,                     -- 受信した event の versionAfter。再配信抑止に使う
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
