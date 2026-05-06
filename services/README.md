# services/

13サービスの実体が並ぶディレクトリ。

## 既存サービス

| サービス | 役割 | 性質 |
|---|---|---|
| [inventory-core](./inventory-core/) | 在庫状態の唯一の書込権威(ADR-0002, ADR-0004) | DBあり / 書込権威 / Outbox / Bridge |
| [inventory-read-model](./inventory-read-model/) | Kafka 購読 → Redis 投影 → 高速参照 | DBなし / 読取専用 / CQRS Read 側 |
| [identity-broker](./identity-broker/) | JWT 発行 + テナント解決(ADR-0007) | DBあり / Pool / JWT 発行側 |
| [audit-service](./audit-service/) | audit.log.v1 全消費 + ハッシュチェーン構築(ADR-0008) | DBあり / Pool / Kafka 消費専用 |
| [master-data](./master-data/) | SKU / Location / Partner マスタ | DBあり / 書込権威 / Outbox / Bridge |
| [notification](./notification/) | 業務イベント駆動の通知配信(MVP は inventory.movement.v1 → 在庫低下メール) | DBあり / Pool / Kafka 消費 / 送信器 port 抽象化 |
| [retail-ec](./retail-ec/) | Day-2 業態系 1 個目: 注文受付 + retail.order.placed.v1 発行 | DBあり / 書込権威 / Outbox / Bridge |
| [tpl](./tpl/) | Day-2 業態系 2 個目: 3PL 入出庫管理 + tpl.stock.movement.v1 発行 | DBあり / 書込権威 / Outbox / Bridge |
| [wholesale](./wholesale/) | Day-2 業態系 3 個目: 法人向け大口受注(取引先別契約価格) + wholesale.order.placed.v1 発行 | DBあり / 書込権威 / Outbox / Bridge |
| [manufacturing](./manufacturing/) | Day-2 業態系 4 個目: BOM(部品構成) + WorkOrder(製造指図) + manufacturing.work_order.released.v1 発行 | DBあり / 書込権威 / Outbox / Bridge |

## サービス間連携 E2E

| シナリオ | テスト | 経路 |
|---|---|---|
| 認証 → 引当 → 投影 → 監査チェーン | [`EndToEndAuthAndReservationFlowIT`](../e2e-tests/src/test/java/com/example/inventory/e2e/EndToEndAuthAndReservationFlowIT.java) | identity-broker → inventory-core → inventory-read-model + audit-service |
| Master Data → 引当 SKU 投影 | [`EndToEndMasterDataInventoryFlowIT`](../e2e-tests/src/test/java/com/example/inventory/e2e/EndToEndMasterDataInventoryFlowIT.java) | master-data → master.product.v1 → inventory-core SKU 投影 |

## 新規サービス追加

[**docs/services/scaffold-guide.md**](../docs/services/scaffold-guide.md) を読む。

決定マトリクス → POMコピー → ドメイン → アダプタ → 配線 の手順が網羅されている。

## 予定サービス(計画 13)

- 共通基盤: Identity Broker ✅ / Master Data ✅ / Inventory Core ✅ / Inventory Read Model ✅ / Audit ✅ / Notification ✅ / Workflow / Integration Hub(7アダプタ) / Analytics
- 業態別: Retail/EC ✅ / Manufacturing ✅ / 3PL ✅ / Wholesale ✅
