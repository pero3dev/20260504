# ADR-0015: Saga は choreography を既定とし、orchestration は条件付きで導入する

- **Status**: Accepted
- **Date**: 2026-05-06
- **Deciders**: Architecture, Platform Team

## Context

業態 4 個すべてが Inventory Core と Saga で連結された(Phase 1+2 / D6 / D9 / D10)。実装過程で、各シーンで choreography(イベント駆動の分散決定)で書くか orchestration(中央コーディネータ)で書くかの判断を都度していたが、判断基準が暗黙のまま蓄積していた。

実装した 4 経路の特徴:

| 経路 | パターン | TX 境界 | 補償 | ステップ数 |
|---|---|---|---|---|
| Retail/EC ↔ Core (Phase 1+2) | choreography | 受注=1TX、引当=1TX | `retail.reservation.failed.v1` → Order.cancel | 2 |
| 3PL → Core (D6) | choreography | 1TX | (なし、DLQ) | 1 |
| Wholesale ↔ Core (D9) | choreography | 受注=1TX、引当=1TX | `wholesale.reservation.failed.v1` → Order.cancel | 2 |
| Manufacturing ↔ Core (D10) | choreography | 指図 release=1TX、消費=1TX(構成要素 all-or-nothing) | `manufacturing.consumption.failed.v1` → WorkOrder.cancel | 2 |

すべて choreography で実装できた事実そのものが本 ADR を書く根拠になる。一方で、既に選定段階で「Workflow サービス(Saga オーケストレータ)」を共通基盤として確保してあり、現状未着手だが**いつ orchestration を導入すべきか**を曖昧にしておくと、後続フェーズ(approval flow, 多段補償, タイムアウト管理)で個別判断がブレる。

業界一般論として:
- **choreography**: 各サービスが自律的にイベントを発行・購読する。ロジックはサービスに分散。中央 SPOF が無い。
- **orchestration**: 中央コーディネータが各ステップを呼ぶ。フローが 1 箇所に集約され、可視性・タイムアウト制御・再開がしやすい。中央 SPOF。

両者は二者択一ではなく、**システム全体では choreography を既定としつつ、特定のフローだけ orchestration を導入する**という混在運用が現実的というのが業界 consensus である(Microservices.io / Newman, Richardson)。本 ADR では本プロジェクトでの線引きを明示する。

## Decision

**Saga は choreography を既定とし、orchestration は以下の条件のいずれかが成立するときに限定的に導入する。**

### Choreography で書く(既定)

以下の特性を持つフローは choreography とする:

1. **ステップ数 ≤ 3、補償 ≤ 1 段**: 「業態系から発火 → Inventory Core が反応 → 失敗時に元サービスが補償」程度。各イベントに対する反応は 1 サービスにつき 1 TX で完結。
2. **タイムアウトの自由度が高い**: at-least-once 配信 + 各 listener が DLQ へ落とす運用で十分。SLA ベースのタイムアウト(例: 5 分以内に確定しないとエスカレーション)が不要。
3. **再開・人手介入が要らない**: 失敗時は補償イベントで自動で巻き戻る。失敗ケースを人が見て手動でリトライする運用が無い、または DLQ + 手動再実行で十分。
4. **可視化が後付けで足りる**: Datadog APM の trace_id 横断で「どこで止まったか」が読めれば十分。専用ダッシュボードで「進行中の Saga 一覧」を見せる必要が無い。

現在実装済みの 4 経路はすべてこれを満たす。

### Orchestration で書く(条件付き、Workflow サービスを使う)

以下のうち **少なくとも 1 つ** を満たす場合は Workflow サービス(Saga オーケストレータ)で書く:

1. **ステップ数 ≥ 4 または補償が多段**: 例: 取引先承認 → 在庫引当 → 物流予約 → EDI 通知 → 請求登録 のような 4 段以上。途中失敗時に「前 N ステップを順番に補償する」必要がある。choreography だと各サービスが他サービスの状態を推測する地獄になる。
2. **SLA 付きタイムアウトが必要**: 「このステップは 5 分以内に終わらなければエスカレーション」「全体で 30 分以内に終わらなければ取消」など。choreography では各サービスがそれぞれタイマを持つ重複が発生する。中央で 1 本のタイマを持つ方が運用が単純。
3. **進行中の Saga インスタンスを人が見たい**: 営業 / オペレータが「このオーダーは今どこ?」と聞いてくる業務。choreography だと各サービスのログを横串で読まないと答えが出ない。orchestration なら 1 テーブルクエリで答が出る。
4. **承認 / 手動介入ステップを含む**: 「与信オーバーは部長承認」「不正検知 hit はコンプラ確認」など、人が non-deterministic に意思決定するステップ。choreography に組み込むと "pause until human" のセマンティクスが分散する。
5. **複雑なルーティング条件**: 「A 業態かつ金額 1000 万超なら approval、そうでなければ skip」のようなフローレベル条件分岐。choreography だと条件判定が複数サービスに重複しがち。

候補としてすでに見えているもの:
- 業態横断の承認フロー(将来の取引先別与信、CFO 承認系)
- Manufacturing の完成品 INBOUND まで含めた指図ライフサイクル全体(release → 部品消費 → 製造完了 → 完成品 INBOUND)— 現状は release → 部品消費 までで切れているが、後続を含めると 4 ステップでタイムアウト要請も生じる
- EDI 取引における取引先 ACK 待ち(時間がかかる、再送が必要)

### 選定フロー

新しい cross-service フローを設計するときの判断手順を文書化する:

```
┌─────────────────────────────────────────┐
│ Q1: ステップ数 ≥ 4 or 補償が多段?        │
│ Q2: SLA タイムアウトが要件?               │
│ Q3: 人が進行状況を見る業務?              │
│ Q4: 承認 / 手動介入を含む?               │
│ Q5: フローレベル条件分岐がある?          │
└─────────────────────────────────────────┘
        │
        ├─ どれか YES → orchestration (Workflow サービス)
        │
        └─ 全部 NO   → choreography (各サービスで listener)
```

### 共存ルール

choreography と orchestration は **同一フローでは混在させない**。一度 orchestration を選んだフローは中央コーディネータで完結させる。逆に choreography 内でこっそり Workflow サービスを呼ぶような実装も禁止(可視性が崩れる)。

ただし **異なるフローを同一プロジェクトで併存** させるのは可。例: 受注 → 引当 は choreography、与信承認は orchestration、で良い。

## Consequences

### Positive

- **設計初期の判断が再現可能になる**。チームが拡大しても 5 つの質問に答えれば判断が割れない。
- **Workflow サービスの実装スコープが明確になる**。MVP では「タイマ + ステップ表 + 進行状況テーブル」あたりで十分で、汎用ワークフロー(BPMN 全機能)は不要。実装した時の評価基準が明確。
- **既存実装が後から非難されにくい**。Phase 1+2 / D6 / D9 / D10 はすべて Q1〜Q5 が NO のシンプルケースで、choreography が正解だったことが明示される。

### Negative

- **境界ケースで揉める可能性は残る**。例: 「ステップ 3 段、SLA は曖昧」で迷ったときの最終裁定者がいない。当面はアーキテクトレビューで個別判断とする。
- **混在運用のメンタルコスト**。choreography と orchestration が同居するシステムは、「このフローはどっち?」を毎回確認する必要がある。ADR-0016(補償トピック分離)とあわせて命名規則で示唆する: `*.failed.v1` で終わるトピックは choreography、 Workflow サービス内のステップは別命名。

### Neutral

- **Workflow サービス自体は別 ADR の対象**。実装方法(Camunda? Temporal? in-house?)は本 ADR では扱わない。
- **再考タイミング**: 半年後 / 業務系イベントが 20 種類超えた時 / 実際に Q1〜Q5 のいずれかを満たすフローが提案された時、本 ADR の有効性を見直す。

## Alternatives considered

### Option 1: orchestration を既定とする

Camunda / Temporal を導入し、すべての cross-service フローを Workflow サービスで書く。

**Rejected**: 現在実装済みの 4 経路すべてが orchestration の利点(タイムアウト、可視性、多段補償)を必要としていない。中央 SPOF を増やすリスク、Workflow エンジン学習コスト、運用コスト(状態 DB の維持、バージョン互換)が現時点では割に合わない。Q1〜Q5 を満たすフローが半年後に複数出てきたら再考に値する。

### Option 2: choreography 一択

Workflow サービスを廃止し、すべて choreography で書く。

**Rejected**: 承認フロー / SLA タイムアウト / 進行可視化が将来要件として確実に出る業務領域(在庫管理 SaaS、特に大企業向け)で、それを choreography に詰め込むと「各サービスが他サービスの状態を推測するアンチパターン」を量産する。Workflow サービス未着手だが選択肢としては残しておく価値が高い。

### Option 3: Saga ライブラリ(Axon, Eventuate)を導入し、書き分けライブラリに任せる

choreography / orchestration の両モードをサポートするフレームワークに乗ると、書き分けが宣言的になる。

**Rejected (for now)**: ADR-0009 で「アグリゲート永続は MyBatis」を選定済みで、JPA 前提の Axon は noisy。Eventuate は導入実績が薄く運用コスト評価できない。手書きでも 4 経路で済んでおり、フレームワーク導入による複雑性の増加が利得を上回る段階ではない。Q1〜Q5 を満たすフローが 5 個超えた時点で再評価に値する。

### Option 4: 質問の数を減らす(Q1 + Q2 のみ)

判断を「ステップ数」と「タイムアウト要件」だけに絞る。

**Rejected**: Q3(可視性)、Q4(承認)、Q5(条件分岐)はそれぞれ独立した orchestration 採用理由になりうる。Q1+Q2 だけだと「ステップ 2 段でも承認を含むフロー」を choreography 側に分類してしまい、後で痛い。5 つすべて残す。

## References

- ADR-0002: Bounded context decomposition(Saga 境界の根拠)
- ADR-0004: CQRS scope inventory-core only(read/write 分離の前提)
- ADR-0009: Transactional Outbox(イベント発行の信頼性根拠)
- 現在実装済みの 4 経路:
  - `services/inventory-core/.../adapter/in/kafka/OrderPlacedListener.java`(Phase 1+2)
  - `services/inventory-core/.../adapter/in/kafka/StockMovementListener.java`(D6)
  - `services/inventory-core/.../adapter/in/kafka/WholesaleOrderPlacedListener.java`(D9)
  - `services/inventory-core/.../adapter/in/kafka/WorkOrderReleasedListener.java`(D10)
- Microservices.io "Pattern: Saga" — choreography / orchestration 比較
- Sam Newman, "Building Microservices" 2nd ed. ch.6 — Saga 議論
- 関連 ADR:
  - ADR-0016: 業態別補償トピック分離(本 ADR と同日提出)
  - ADR-0017: Reserve のみ vs Reserve+Ship(本 ADR と同日提出)
