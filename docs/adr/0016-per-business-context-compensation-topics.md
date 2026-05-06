# ADR-0016: 補償イベントは業態(business context)ごとにトピックを分離する

- **Status**: Accepted
- **Date**: 2026-05-06
- **Deciders**: Architecture, Platform Team

## Context

業態系から Inventory Core への引当 / 消費が失敗したときに、元サービスに状態巻き戻し(Order.cancel / WorkOrder.cancel)を要求する **補償イベント** を Inventory Core が発行する。Phase 2(Retail/EC)実装時に最初の補償トピック `inventory.reservation.failed.v1` を作り、共有トピックとして 1 本で済ませる選択肢があった。

その後の D9(Wholesale)/ D10(Manufacturing)で実装を増やすにあたり、**1 本の共有トピックで済ませるか、業態ごとに分離するか**を選定する必要が生じた。最終的に分離を採用しているが、判断根拠を ADR で明示しておかないと、後続の業態追加(取引先協業、サブスクリプション等)で同じ判断を毎回することになる。

実装済みの補償トピック:

| 業態 | トリガ | 補償トピック | 購読サービス |
|---|---|---|---|
| Retail/EC | `retail.order.placed.v1` | `retail.reservation.failed.v1` | retail-ec |
| Wholesale | `wholesale.order.placed.v1` | `wholesale.reservation.failed.v1` | wholesale |
| Manufacturing | `manufacturing.work_order.released.v1` | `manufacturing.consumption.failed.v1` | manufacturing |
| 3PL | `tpl.stock.movement.v1` | (なし、DLQ) | (該当なし) |

注: Retail/EC の補償トピックは Phase 2 で当初 `inventory.reservation.failed.v1`(共通名前空間)として実装されていたが、本 ADR の Decision に従い L4 タスクで `retail.reservation.failed.v1` に改名済み(commit 履歴参照)。本プロジェクトはまだ本番投入前のためアトミック改名で対応(下流の Datadog ダッシュボード等は未敷設)。

業界慣行としては:
- **共有トピック派**: 「在庫引当失敗」という事実は普遍で、業態別に重複したトピックを増やすのは命名汚染。`originContext` フィールドで購読側が振り分ければよい。
- **分離派**: 各業態は独立した bounded context。境界をまたぐイベントは契約として独立しているべき。共有すると「取引先別に Schema を変えたい」「リトライ戦略を変えたい」が衝突する。

## Decision

**補償イベントは業態ごとに独立したトピック名前空間を持つ**(`<業態>.<操作>.failed.v<N>`)。

具体的に:

1. **トピック命名規則**:
   - トリガイベント: `<業態>.<対象>.<状態変化>.v<N>`(例: `wholesale.order.placed.v1`)
   - 補償イベント: `<業態>.<操作>.failed.v<N>`(例: `wholesale.reservation.failed.v1`、`manufacturing.consumption.failed.v1`)
   - 業態 prefix は ADR-0002 の bounded context と一致させる(retail / wholesale / manufacturing / tpl)
2. **発行側**: Inventory Core(共通基盤)が業態を認識して**業態別トピックに発行する**。Inventory Core 内の Listener は `XxxOrderPlacedListener` のように業態ごとに分かれており、各 Listener が対応する補償 UseCase(`EmitWholesaleReservationFailedService` 等)を呼ぶ。
3. **Schema**: 補償イベント Schema は業態間で構造的にほぼ同じだが**型としては別**(別 record class)。共通親型を継承させない。共通フィールド(`aggregateId, code, errorCode, reason, occurredAt`)に加え、業態固有のヒント(Wholesale: `failedSkuCode/failedLocationId`、Manufacturing: `failedComponentSkuCode/failedLocationId`)を持つ。
4. **購読側**: 各業態サービスは自業態の補償トピックのみ購読する(他業態を購読しない)。Group ID は `<service>-compensation` で固定。
5. **既存の Phase 2 トピック改名**: `inventory.reservation.failed.v1` を `retail.reservation.failed.v1` に改名する。L4 タスクで実施完了(本 ADR コミット直後)。本プロジェクトは本番投入前のためアトミック改名で対応した。本番運用後に同様の改名が発生した場合は、新トピック並行発行 → 購読切替 → 旧トピック sunset の段階移行が必要。

## Consequences

### Positive

- **業態別の運用調整が独立**: リトライ戦略 / Datadog アラート閾値 / 監査保管期間 / DLQ ポリシーを業態ごとに変えられる。例: Wholesale は B2B で SLA が厳しいので即時アラート、Retail/EC は B2C で大量・低単価なのでバッチ集計でよい、というポリシ差を Kafka 設定レベルで反映できる。
- **Schema 進化が独立**: 業態固有のフィールドを追加しても他業態のスキーマレジストリ互換チェックに影響しない。例えば Manufacturing が `failedComponentLineNo` を後で追加しても Wholesale 購読は影響を受けない。Glue Schema Registry(本プロジェクト採用)で `BACKWARD_TRANSITIVE` の compatibility level を業態ごとに設定可能。
- **購読側の責務が単純**: 「自業態の補償だけ処理する」と決まっており、`originContext` で振り分けるロジックが不要。listener が他業態のメッセージを取り逃しすることが構造的に発生しない(購読していないので)。
- **監査ディメンションが業態別になる**: J-SOX 観点で「Wholesale 業態の補償発生件数 / 月」のような切り口が Kafka topic 単位で集計できる。共有トピックだと WHERE 句で `originContext='wholesale'` を毎回書く必要が生じる。
- **Saga 境界の明示**: ADR-0015 で choreography を既定としたが、業態別トピック分離は **「この Saga は Wholesale が主体で Inventory Core は受動的」** という関係を物理的に表明している。共有トピックだとこの主従関係が曖昧になる。

### Negative

- **Inventory Core の発行コードが業態数だけ増える**: `EmitOrderReservationFailedService`(Retail/EC)、`EmitWholesaleReservationFailedService`(Wholesale)、`EmitWorkOrderConsumptionFailedService`(Manufacturing)が並ぶ。共有なら 1 クラスで済む。命名重複は **境界の表明だと割り切る**(Conway's Law 視点で、Inventory Core はあくまで 4 業態それぞれと独立に契約している)。
- **トピック数が業態数 × 操作数**: 業態 5 個 × 補償 2 種類 = 10 トピックが補償だけで増える。Glue Schema Registry の管理対象が増える。Kafka cluster の partition プールへの圧力は微小だが、運用ポリシ管理(retention, ACL)が増える。
- **Pact / contract test を業態 × 補償ごとに書く必要**: 業態ごとの補償スキーマを契約として固定する必要がある。共有なら 1 契約で済む。ただし契約テストは将来別 ADR で扱う想定。
- **共通改善の伝播コスト**: 例えば「すべての補償イベントに新フィールド `compensatingEventId` を入れる」という改善を全業態に適用するときは、業態数だけ Schema 変更 PR + Producer 改修 + Consumer 改修が要る。共有なら 1 セットで済む。

### Neutral

- **Internal イベント(同一業態内のみで完結する)は引き続き業態 prefix で 1 本**。例えば `retail.order.cancelled.v1` を Notification が購読、のような流れは業態内の一貫名前空間で十分。本 ADR は **業態境界をまたぐ補償** に対する規定。
- **将来共通基盤の補償が必要になった場合(例: Identity Broker のテナント停止)**は `platform.<操作>.failed.v1` 名前空間を別途定義する。本 ADR は業態系のみ規定。

## Alternatives considered

### Option 1: 単一共有トピック `inventory.reservation.failed.v1` + originContext フィールド

すべての補償を 1 トピックに集約し、購読者は `originContext` で振り分ける。

**Rejected**: 主な理由は **「購読者が他業態のメッセージを受信し続ける」** こと。Wholesale サービスが Retail/EC や Manufacturing の補償も deserialize する必要があり、無視メッセージのオーバーヘッドが発生する。Group ID 別に分けても broker から読み出す事自体は発生する。さらに Schema 進化で「Manufacturing が新フィールド追加 → Wholesale の deserializer がエラー(unknown property)」の事故リスクが高い(`@JsonIgnoreProperties(ignoreUnknown = true)` で吸収はできるが、業態別契約の意味的境界が崩れる)。リトライ戦略 / 監査ディメンションが業態混在になる運用上の負担も大きい。

### Option 2: 共通親トピック + 業態別子トピック(Kafka MirrorMaker / Topic chain)

Inventory Core は `inventory.compensation.v1` に発行 → MirrorMaker / Kafka Streams で業態別トピックに分配。

**Rejected**: 中間処理ノードが SPOF になる。失敗時に「どこで止まったか」のデバッグが多段になる。Glue Schema Registry の管理対象も結局増える(中間トピック + 終端トピック)。最大の問題は Inventory Core が「分配が完了したか」を知らないため、補償が業態に届いた保証が transactional outbox でカバーできない範囲に出る。

### Option 3: `originContext` で振り分けつつトピックは業態別

両方の利点を取ろうとして、業態別トピック + メッセージ内に `originContext`/`originService` メタを持つハイブリッド。

**Partially adopted**: 実は本 ADR の決定でも、メッセージのメタとして `tenant_id`、`trace_id`、`schema_version` を Kafka header に持っているので、ある程度のメタは共有している。`originContext` は冗長(トピック名で既に表明)なので**追加しない**。purpose は明示されているのでハイブリッドの中間案ではなく、「メタは tenant + trace に絞る」決定として収束。

### Option 4: 補償自体を Kafka でなく gRPC 同期コールバックで実装

Inventory Core が業態サービスに直接 gRPC で「この Order を cancel して」と呼ぶ。

**Rejected**: ADR-0009(Transactional Outbox)と矛盾する。Kafka を経由しない「同期失敗通知」は信頼性が下がる(業態サービス側が落ちていたら補償が失われる)。可視性も下がる(Kafka なら audit-service が拾うが、gRPC は別途記録が必要)。

## Consequences for new business contexts

新しい業態(例: Subscription、Marketplace)を追加するときの手順:

1. ADR-0015 の判断フローで choreography が選定されたら、補償トピックを `<業態>.<操作>.failed.v1` で定義
2. Inventory Core 側に `Emit<業態>XxxFailedUseCase` + Service を新設(`@Transactional(REQUIRES_NEW)` で別 TX)
3. Inventory Core 側 Listener から失敗時に上記 Service を呼ぶ
4. 業態側に `Handle<業態>FailureService` + Listener を新設

参考実装:
- `services/inventory-core/.../application/usecase/EmitWholesaleReservationFailedService.java`
- `services/inventory-core/.../application/usecase/EmitWorkOrderConsumptionFailedService.java`
- `services/wholesale/.../application/usecase/HandleReservationFailureService.java`
- `services/manufacturing/.../application/usecase/HandleConsumptionFailureService.java`

## References

- ADR-0002: Bounded context decomposition(業態 = bounded context の根拠)
- ADR-0009: Transactional Outbox(補償発行の信頼性根拠 — `REQUIRES_NEW` 別 TX)
- ADR-0015: Saga choreography as default(本 ADR と同日提出 — choreography 前提)
- AWS Glue Schema Registry — 業態別 compatibility 設定の手段
- 現在実装済みの補償経路:
  - Retail/EC: `services/inventory-core/.../EmitOrderReservationFailedService.java` ↔ `services/retail-ec/.../HandleReservationFailureService.java`
  - Wholesale: `services/inventory-core/.../EmitWholesaleReservationFailedService.java` ↔ `services/wholesale/.../HandleReservationFailureService.java`
  - Manufacturing: `services/inventory-core/.../EmitWorkOrderConsumptionFailedService.java` ↔ `services/manufacturing/.../HandleConsumptionFailureService.java`
- 関連 ADR:
  - ADR-0015: Saga choreography(本 ADR と同日提出)
  - ADR-0017: Reserve のみ vs Reserve+Ship(本 ADR と同日提出)

## Follow-up tasks

- ~~`inventory.reservation.failed.v1` → `retail.reservation.failed.v1` 改名~~ — L4 タスクで完了(本プロジェクトは本番投入前なのでアトミック改名で対応)
