# ADR-0017: 在庫消費パターンは Reserve のみ / Reserve+Ship を業務シナリオで使い分ける

- **Status**: Accepted
- **Date**: 2026-05-06
- **Deciders**: Architecture, Platform Team

## Context

Inventory Core の `Inventory` 集約は `reserve(reservationId, quantity)` と `ship(quantity)` の 2 操作を持ち、状態は `available` / `reserved` の 2 つの量で管理される。

```
              reserve(qty)         ship(qty)
available ─────────────────► reserved ─────────► (out of system)
   │                           │
   │      release(qty)         │
   ◄───────────────────────────┘
```

業態が Inventory Core を呼ぶ時、`reserve` だけ呼ぶのか、続けて `ship` まで呼ぶのか(=即時に系外へ出すのか)はビジネスシナリオで分かれる。実装した 4 経路で異なる選択をしている:

| 経路 | パターン | 後続でのフロー |
|---|---|---|
| Retail/EC ↔ Core (Phase 1+2) | **Reserve のみ** | 出荷確定時(別フロー)に ship を呼ぶ |
| Wholesale ↔ Core (D9) | **Reserve のみ** | 出荷確定時(未実装)に ship を呼ぶ |
| 3PL OUTBOUND (D6) | **Reserve+Ship** | 同 TX で完結。後続フローなし |
| Manufacturing ↔ Core (D10) | **Reserve+Ship** | 同 TX で完結。後続フローなし |

判断は実装時に都度していたが、**「いつ Reserve のみで、いつ Reserve+Ship か」** の基準が暗黙のままだった。新業態追加時(将来の Subscription、Marketplace)にこの判断を毎回する必要があり、また既存の Wholesale / Retail/EC は ship 工程が未実装(integrate されておらず、仕様上の宿題)で、フローを閉じる時に方針がブレないよう ADR で固定する。

## Decision

**「ビジネス上、後で訂正される可能性が残っているか」で使い分ける**:

- **Reserve のみ**: 訂正される可能性が残っている工程の引当(オーダ受付、契約確定)
- **Reserve+Ship**: 物理的に消費 / 出荷が確定し、訂正は新たな逆方向トランザクション(返品、補正)でのみ可能なシナリオ

### Reserve のみを使うシナリオ(訂正可能性が残る)

以下のシナリオでは Reserve だけ呼び、ship は別工程(出荷確定 UseCase)で行う:

1. **B2C 受注(Retail/EC)**: 顧客は出荷前にキャンセルできる(在庫返還が必要)
2. **B2B 受注(Wholesale)**: 取引先からの取消、納期延期、数量訂正が日常的に発生する
3. **将来の Subscription**: 月次自動引当だが解約 / プラン変更で訂正される可能性
4. **取引先内示 / 仮押さえ**: 確定前のホールド

これらでは「reserved にしておいて、出荷指図が確定したら ship」というフローが業務上 natural。引当だけ済ませて出荷工程に渡し、出荷工程側で別 UseCase が ship を呼ぶ。

### Reserve+Ship を使うシナリオ(訂正不可、物理消費確定)

以下のシナリオでは reserve と ship を同 TX で連続実行する:

1. **3PL OUTBOUND**: 倉庫管理から「出庫しました」というイベントが既に物理現実を後追いしている。在庫から外す確定処理。
2. **Manufacturing 部品消費**: 製造指図 release は「製造ラインで部品が物理的に消費される」を意味する。reserved のまま残ると完成品ができたとき帳尻が合わない。
3. **将来の即時消費系**: 例えば BtoC でも飲食店向けの出庫即消費(別ロケーション再配置)、自動倉庫からのピッキング即引落、など。

判定基準は「**この呼び出しは物理現実(出荷済み / 製造で消費済み)を反映しているか? それとも将来のキャンセル可能性が残った仮押さえか?**」。物理現実を反映している場合は ship まで実行する。仮押さえなら reserve で止める。

### 設計上の制約

`Reserve+Ship` は Inventory Core の集約ルート上では `Inventory.reserve(rid, qty)` → `Inventory.ship(qty)` の 2 メソッド呼び出しになる。これは domain の対称性を保つために残しており、`directShip(qty)` のような 1 メソッドの近道は **作らない**。理由:

- `reserved` を 0 ベースで通過するのは明示的な在庫状態遷移。Audit / 整合性監査で「いつ reserved に乗ったか」が観察できる(将来 SHA-256 hash chain の根拠データになる)
- `Inventory.ship()` は引数として `reserved >= qty` を要求する domain invariant を持つ。`reserve()` を経由せずに直接 ship を呼ぶ抜け道を作ると invariant 違反検出が弱くなる
- 多くのシナリオ(Reserve のみ → 後で ship)で `ship` 単独使用ニーズがあるので、結局 `ship` は単独で公開する必要がある。`reserve+ship` の合成は呼び出し側でループ + 2 回呼ぶで十分

なお Manufacturing で`ConsumeWorkOrderComponentsService` が複数構成要素に対して reserve+ship を順番に実行する設計は、**1 TX 内** で all-or-nothing になる前提の実装。`@Transactional` がついていれば 2 回目の構成要素で `InsufficientStockException` が出ても 1 回目の reserve+ship は rollback される。

### 命名の一貫性

UseCase メソッド名は以下の規則で揃える:

- `ReserveOrderUseCase.reserveForOrder(...)` — Reserve のみ
- `ConsumeWorkOrderComponentsUseCase.consume(...)` — Reserve+Ship(物理消費を意味する `consume` 動詞)
- `ApplyStockMovementUseCase.apply(...)` — 3PL のような汎用エンドポイント、内部でタイプによって分岐

`consume` の動詞が出てきたら Reserve+Ship、`reserve` のときは Reserve のみ、と読めるよう揃える。

## Consequences

### Positive

- **Manufacturing / 3PL のフロー閉じが正しい**: 物理現実(製造ラインで部品消費 / 倉庫から物理出庫)に対応するイベント受信時に reserved 残が宙ぶらりんにならない。Audit 上「reserved → 0」のトランジションが残るので整合性監査が打てる。
- **業態追加時の判断が再現可能**: 新業態を加える時、4 つのシナリオに当てはめて Reserve のみ / Reserve+Ship を選ぶだけ。曖昧さが消える。
- **Wholesale / Retail/EC の ship 工程実装が後ろ倒しできる**: 「Reserve だけしてあとは未実装」が技術的負債ではなく **本 ADR の方針通りの中間状態** になる。後で出荷確定 API + UseCase を追加すれば閉じる(後述の Follow-up tasks)。
- **`ApplyStockMovementService`(3PL)の OUTBOUND 実装が ADR 的に裏付けられる**: コメントで「reserve+ship を即時実行で重ねて表現する」と書いていた選択が、本 ADR で明示された原則と整合することが確認できる。

### Negative

- **Reserve のみ系の呼び出し側に責任が残る**: Wholesale / Retail/EC では「ship を呼ぶ別 UseCase」が必要で、未実装。受注したまま発送したのに在庫が reserved のまま残る漏れリスクは ADR ではなく実装で解消する必要がある(Follow-up tasks で task 化)。
- **設計判断のメンタルコスト**: 新シナリオで Reserve のみか Reserve+Ship か考える必要があり、ゼロコストではない。が、4 つの判定基準で割り切れる範囲なので軽微。
- **Audit 件数が増える**: Reserve+Ship シナリオでは 1 業務操作あたり 2 つの状態遷移を記録することになる。MyBatis Outbox + audit-service への流量が増える。実測が必要だが、現時点で見積もる必要は無い。

### Neutral

- **return / 返品 / 補正は別フロー**: 物理消費後の訂正は **新しい逆方向トランザクション**(`receive(qty)` で在庫を戻す)で行う。Reserve+Ship 後にロールバック的に「ship を取消」は許さない。これは ADR-0009 の event sourcing 風の考え方と整合する(過去のイベントは消さない、新しい打ち消しイベントを足す)。
- **3PL ADJUSTMENT は MVP 未対応**: `ApplyStockMovementService` で `ADJUSTMENT` は `LOG.warn + return`。返品処理 / 補正専用 UseCase が将来必要になるが、本 ADR では扱わない。

## Alternatives considered

### Option 1: すべて Reserve のみ + 別工程で ship

Manufacturing の部品消費も「reserve しておいて、別途完成品 INBOUND イベントを受けて ship」とする。

**Rejected**: Manufacturing release は物理消費を反映するイベントなので、reserved 残のまま完成品 INBOUND を待つ間「在庫帳簿上は減っていない」状態が続く。問い合わせ時の在庫表示と物理倉庫の差異が業務上の問題になる。3PL OUTBOUND も同様で、出庫済の在庫が available にあるように見える(別出庫指図と二重引当の事故が起きうる)。

### Option 2: すべて Reserve+Ship で書き、訂正は逆方向トランザクションで

Wholesale / Retail/EC でも Reserve+Ship、キャンセル時は `receive(qty)` で在庫戻し。

**Rejected**: 業務上「reserved にしておくべき期間」が明示的に発生する(出荷指示までの保留、出荷スケジューリング)。Reserve+Ship を即時実行して必要に応じて戻すと、その期間中の在庫表示が正しくない(available が見かけ上減っている)。Wholesale で重要な「複数注文の引当競合検知」「与信枠との突合」は reserved 状態があってこそ機能する。受注から出荷までのリードタイム中、業務担当が「いまどれだけホールドされているか」を見たい要件もある。

### Option 3: domain に `directShip(qty)` を追加

reserve を経由しない 1 メソッドショートカットを追加して呼び出し側のループを簡潔にする。

**Rejected**: ADR 本文の "設計上の制約" 節で述べた通り、reserved を経由する明示性 / invariant 検証 / 監査メリットが大きい。1 メソッド合成のメリットは呼出 1 行省略のみで、価値が低い。

### Option 4: シナリオ別に集約ルートを分ける

`ImmediateConsumption` 集約と `ReservationBased` 集約を別に作り、操作セットを分離する。

**Rejected**: ADR-0002 で Inventory を 1 集約とする決定をしており、それを変更する根拠が無い。Inventory レコード(SKU × Location)は同一物で、業態によって扱い方が異なるだけ。集約を分けると同じ物理在庫レコードが複数集約に表現される矛盾が発生する。

## Follow-up tasks

本 ADR を機に実装の遅れを task 化:

1. **Wholesale OUTBOUND フロー実装**: SalesOrder 受注 → Reserve(D9 で実装済) → 出荷確定 API → `Inventory.ship` 呼出。Audit、Outbox 含めて受注から出荷までのフローを閉じる。
2. **Retail/EC OUTBOUND フロー実装**: 同上。Phase 1+2 で Reserve まで来ているのを ship まで延長。
3. **Manufacturing 完成品 INBOUND 実装**: WorkOrder.complete 時に完成品 SKU の `Inventory.receive(qty)` を呼ぶフロー。Reserve+Ship で部品が消えた裏側で完成品を増やす。これがないと物理現実(完成品が出来た)と帳簿(部品だけ消費されて完成品在庫が増えていない)が乖離する。

## References

- ADR-0002: Bounded context decomposition(Inventory 集約の境界)
- ADR-0009: Transactional Outbox + ORM MyBatis(集約永続の方針)
- ADR-0015: Saga choreography as default(本 ADR と同日提出)
- ADR-0016: 業態別補償トピック分離(本 ADR と同日提出)
- 現在実装済みの 4 経路:
  - Reserve のみ: `services/inventory-core/.../ReserveOrderService.java`
  - Reserve+Ship(汎用): `services/inventory-core/.../ApplyStockMovementService.java#L54-L61`(OUTBOUND 分岐)
  - Reserve+Ship(WorkOrder 専用): `services/inventory-core/.../ConsumeWorkOrderComponentsService.java`
- Domain 集約:
  - `services/inventory-core/src/main/java/com/example/inventory/core/domain/model/Inventory.java`
