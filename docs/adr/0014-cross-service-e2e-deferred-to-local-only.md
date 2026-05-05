# ADR-0014: Cross-service E2E tests are local-only; CI uses single-context Kafka IT

- **Status**: Accepted
- **Date**: 2026-05-06
- **Deciders**: Architecture, Platform Team
- **Supersedes**: 部分的に ADR-0012(F5: PR contract & CI gates)の `mvn verify` 範囲解釈

## Context

Day-1 で **多重 Spring Context を 1 JVM に同居させる cross-service E2E IT** を `e2e-tests/` モジュールに用意した:

- `EndToEndAuthAndReservationFlowIT` — identity-broker → inventory-core → inventory-read-model + audit-service の 4 サービス連結
- `EndToEndMasterDataInventoryFlowIT` — master-data → master.product.v1 → inventory-core 投影
- `EndToEndReservationFlowIT` — inventory-core → inventory-read-model

意図は「サービス境界を跨いだ Kafka イベント / 認証 / 監査チェーン整合の 1 JVM での実証」。Testcontainers で Postgres + Kafka + Redis を起動し、各サービスを `SpringApplicationBuilder.run()` で別 ApplicationContext として起動する設計。

しかし 2026-05-05 の CI 初回実行で連続的な不安定さが顕在化した:

1. 4 service × HikariPool 既定 30 で Postgres `max_connections=100` を超過し、`OutboxPublisher` の `CannotCreateTransactionException`(プール絞りで部分対応した)。
2. Kafka producer の `Connection to node 1 could not be established`(Kafka コンテナ自体は生きている)。
3. テストクラス間遷移で Postgres / Kafka 両方の接続が断続的に不能になる。
4. `Surefire is going to kill self fork JVM. The exit has elapsed 30 seconds after System.exit(0).` — 非デーモンスレッド残留で JVM hang。

ローカル開発環境(Windows + Docker Desktop 4.71)では Docker Desktop が hardened named pipe ガードを入れて Java SDK 接続を 400 で拒否することも判明し、TCP 露出 + `DOCKER_HOST=tcp://localhost:2375` の手動設定が前提になる。

つまり **多重 Context 同居 + Testcontainers のリソース寿命管理 + 各サービス内のスケジュール実行(Kafka publisher / consumer / @Scheduled)が絡む面**を一度に検証しようとしており、ある層を直すと別の層が露出する不安定さが残った。

並行して、**`services/inventory-core` 配下に単一 Spring Context の Kafka IT(`KafkaIntegrationE2ETest`)を新設**し、そちらは CI 上で安定して以下を実証できることが分かった:

- Reserve API → Outbox → Kafka publisher → `inventory.movement.v1` 配信
- `master.product.v1` Kafka 受信 → `SkuMasterListener` → `sku_registry` 投影
- 未登録 SKU での Reserve → 422 `ERR_UNKNOWN_SKU`

## Decision

**cross-service E2E(`e2e-tests/*IT.java`)を CI から外し、ローカル開発時のオプション扱いにする。** CI の Kafka 経路実証は `services/inventory-core/.../KafkaIntegrationE2ETest`(単一 context、surefire 経路)が担う。

具体的には:

- `e2e-tests/pom.xml` の `<properties>` に `<skipITs>true</skipITs>` を既定としてピン。
- `.github/workflows/ci.yml` は `mvn -B -ntp --fail-at-end verify` のまま(`-DskipITs=false` 指定なし)。
- ローカル / 将来の hardening 作業時は `mvn -DskipITs=false verify` で明示的に有効化する。
- `e2e-tests/*IT.java` は **削除しない**。マルチサービス連携の reference implementation としてコードベースに残し、テスト技術や Spring Boot バージョンが進化した時に活かせる状態を保つ。
- `KafkaIntegrationE2ETest` は **canonical CI Kafka validation** として位置付ける。新サービス追加時の Kafka 経路検証は、まず本パターンを踏襲して各サービス内に単一 context IT を追加する方針とする。

## Consequences

### Positive

- **CI が green を維持しやすくなる。** 多重 context のリソース cleanup に追われずに、本来検証したい "コードの意味的な正しさ" にフォーカスできる。
- **単一 context IT は本番の "1 サービス = 1 Pod" 構成に近い**。本番では各サービスが別 JVM・別ホストなので、Kafka や DB へ接続するのは 1 Pod の HikariPool だけ。多重 context は本番には存在しない構成を検証している側面があり、本番代表性の点でも単一 context のほうが有意味。
- **新サービス追加時のコスト低減**。Notification / Workflow / 業態系を追加しても、cross-service IT の同居数を増やすことなく、単一 context IT を 1 本ずつ書き足すだけで Kafka 経路検証を維持できる。

### Negative

- **複数サービスにまたがる "choreography" の検証が CI 外**に出る。例: identity-broker のログイン → JWT → inventory-core の権限解決 → audit-service のチェーン検証、という 4 サービス通しのフロー保証は CI で得られない。各サービスの単体 IT と Pact 等のコントラクトテストで段階的に補う必要がある(将来 ADR で扱う)。
- **regression 検出が分散する**。単一 context IT は各サービスに分散するため、e2e-tests が 1 箇所で見ていた "繋がっているか" の確認がサービス単位の責務に分散する。

### Neutral

- 多重 context IT は **将来のローカル / nightly hardening タスク** として ToDo に残る(`task #41` 系列)。Spring Boot 4 / Testcontainers 進化 / 専用 Pod 起動 (e.g., `docker compose` 系)で安定化できる可能性は高いが、本決定時点の優先度は低い。
- ローカル開発で多重 context IT を回すことは妨げない。Docker Desktop の TCP 露出 + `DOCKER_HOST` を設定すれば `mvn -DskipITs=false verify` で実行できる(CLAUDE.md "Local vs CI test boundaries" 参照)。

## Alternatives considered

### Option 1: 多重 context を CI で動かしきる(数日の hardening 投資)

HikariPool 競合は pool size 5 で対処、JVM hang は `@DirtiesContext` + 明示的な `applicationContext.close()` 強制、Kafka producer は `flush()` を AfterAll で呼ぶ、など個別の手当てを積み上げる。

**Rejected**: 修正の予測可能性が低い。1 つ修正するごとに別の症状が出るパターンが連続しており、修正収束時期が読めない。CI を不安定なまま long-running PR にしておく弊害(merge のたびに緊張する、retry の社会的コスト)が大きい。Day-1 の他の作業価値を侵食する。

### Option 2: e2e-tests モジュールを削除

reference implementation の価値を失う。cross-service の挙動を検証する技術スタック(Testcontainers + 多重 SpringApplicationBuilder + Kafka 直接 producer/consumer)の参考実装を残しておく価値はあり、コード行数は CI コストに影響しない。

**Rejected**: 削除コストが高く戻すコストも高い。`<skipITs>true</skipITs>` ゲートで実行を止めるだけで十分。

### Option 3: Kafka 経路検証を nightly schedule (`workflow_dispatch`) で別 job に分離

毎晩 1 回 cross-service IT を回し、失敗してもその日のうちに気付く運用。

**Rejected (now, may revisit)**: nightly job 自体は妥当だが、本決定時点で「nightly が赤になっても誰も alert を取りに行く運用が整っていない」状況のほうが先に解決すべき問題。alerting / on-call 体制の確立後に本オプションは再考に値する。

### Option 4: Pact (consumer-driven contract test) で cross-service interaction を検証

Producer 側(例: inventory-core)のスキーマ契約を consumer 側(例: read-model)が verify する。実 broker 不要で軽量。

**Future complement**: 単一 context IT の "意味的な正しさ" + Pact の "型契約" + (将来の) nightly E2E の "結合動作" の三層を組み合わせるのが本来の姿。Pact 導入は別 ADR(0015 候補)で扱う。

## References

- ADR-0009: Transactional Outbox(`OutboxPublisher` の self-invocation 修正の経緯はこの ADR の補足記述として `commit 5089e58` を参照)
- ADR-0012: Trunk-Based Development(F5 PR contract と CI ゲートの位置付け)
- `CLAUDE.md` "Local vs CI test boundaries" 節 — 開発者向け手順
- `services/inventory-core/src/test/java/com/example/inventory/core/e2e/KafkaIntegrationE2ETest.java` — canonical Kafka 経路検証
- `e2e-tests/*IT.java` — 多重 context 参考実装(skipITs=true で gate)
- 本決定の経緯コミット: `cc73e82`(CI 設定)、`00c50c5`(YAML 修正)
