# ADR-0019: Pact による Consumer-driven 契約テストを段階導入する

- **Status**: Accepted
- **Date**: 2026-05-07
- **Deciders**: Architecture, Platform Team

## Context

ADR-0014 で「cross-service E2E は CI 外しに、KafkaIntegrationE2ETest 単一 context IT を canonical にする」と決めたとき、Future complement として「Pact 等の contract test を別 ADR で扱う」と書いた。本 ADR でその検討を実施する。

現状のテスト構成:

| 層 | ツール | カバー範囲 |
|---|---|---|
| 単体 / ArchUnit | JUnit 5 + ArchUnit | クラス内 / 層構造 |
| サービス内 IT | Spring Boot Test + Testcontainers | DB / Kafka 経路、 Outbox 込み |
| Cross-service IT | (CI 外し、ローカルのみ) | 多重 Spring Context、現状 fragile |
| Cross-service "意味的な正しさ" | KafkaIntegrationE2ETest(単一 context) | サービス境界の Kafka 経路 |

ギャップ: **Producer / Consumer 間の Schema 契約の自動検証**。例えば Wholesale が `wholesale.order.placed.v1` に新しい必須フィールドを追加すると、 inventory-core 側の `WholesaleOrderPlacedListener` が Jackson デシリアライズで失敗する。これは:

- 単体テストでは検出されない(各サービス独立のため、相手の payload を知らない)
- KafkaIntegrationE2ETest は payload 形式を hardcode しているので「現実装のスキーマ」しか検証できない(現実装が変更された後に検出するのではなく、変更を未然防止したい)
- 多重 context IT は CI 外し中(ADR-0014)
- AWS Glue Schema Registry(本プロジェクト採用)は **構造的互換性**(BACKWARD_TRANSITIVE 等)はチェックするが、**Consumer がそのフィールドを実際に使っているか / どの値域を期待しているか** はチェックしない

業界慣行として、サービス間メッセージング契約には **Consumer-driven Contract Test (CDCT)** が定石。Pact / Spring Cloud Contract が代表的。Pact-JVM は Java + Kafka メッセージングをサポート。

CDCT の核心原理:
1. **Consumer が「自分が必要とする payload 形式」を契約として書く**
2. Pact ファイルが生成される(JSON)
3. Provider 側で **同じ Pact ファイルを verify**:Provider の実出力が Consumer の契約を満たすかチェック
4. Provider 側の変更が Consumer の契約を破ると CI で検出される

Glue Schema Registry が「文法レベルの後方互換」を担うのに対し、Pact は「実使用ベースの後方互換」を担う。**両者は補完関係** であり、どちらかで他を代替できない。

## Decision

**Pact (au.com.dius.pact) を Consumer-driven 契約テストとして段階導入する。MVP は Consumer 側のみ、Provider verify と Pact Broker 連携は後続段階とする。**

### Phase 1(本 ADR コミット時点で実装済み)

- **Consumer 側 Pact テストを書く**: `inventory-core` で `wholesale.order.placed.v1` の期待形式を Pact V4 API で記述
  - 場所: `services/inventory-core/src/test/java/.../pact/WholesaleOrderPlacedConsumerPactTest.java`
  - 出力: `target/pacts/inventory-core-wholesale.json`(コミット [`025803f`](https://github.com/pero3dev/20260504/commit/025803f))
- **契約スコープは Consumer が実使用するフィールドのみ**(consumer-driven の本質): `aggregateId / code / items[].{lineNo,skuCode,locationId,quantity} / occurredAt`。Consumer が見ない `partnerCode / unitPrice / requestedDeliveryDate` 等は契約外。
- **Pact ファイルはリポジトリ commit しない**(`target/pacts/` は `target/` 配下なので gitignore 済み)。Phase 2 で Pact Broker または artifact pass 経由で Provider に渡す。

### Phase 2(後続タスク)

優先度順:

1. **Provider verifier テスト**: `wholesale` 側で `inventory-core-wholesale.json` を読み込み、`SalesOrderPlacedEvent` の実 JSON 出力が契約を満たすか verify する JUnit テスト。Pact ファイルは local 共有(両モジュールが同 reactor 内なので相対パス参照可)。
2. **同パターンの契約を増やす**: `retail.order.placed.v1`、`wholesale.order.shipped.v1`、`wholesale.order.cancelled.v1`、`manufacturing.work_order.released.v1`、`manufacturing.work_order.completed.v1`、 `manufacturing.consumption.failed.v1`、 `master.product.v1` 等。各 Consumer 視点で書く。
3. **Pact Broker 連携**(後続課題): Broker(または Pactflow)を立てて contract publish + verification 結果集約。複数 PR 間のスキーマ変更影響(`can-i-deploy`)が機械的に判定可能になる。MVP では reactor 内で十分。

### Phase 3(2026-05-07 実施分)

Phase 2.2 で contract が 5 件まで増えたため Phase 2.3 の re-evaluation 条件を満たし、 Broker 導入と `can-i-deploy` 自動化を実施した。

**実装したもの**:

- **Pact Broker の local docker-compose 提供**: `infra/pact-broker/docker-compose.pact-broker.yml` に postgres + pactfoundation/pact-broker 2.115.0.1 のスタックを配置。 起動方法・credential 等は `infra/pact-broker/README.md` 参照。
- **inventory-core に pact-jvm-provider-maven plugin 追加**: `mvn -pl services/inventory-core pact:publish` で `target/pacts/*.json` を Broker に上げる経路を確立。 phase 非 bind(明示実行のみ)。
- **CI workflow `pact-broker.yml` 追加**: main push で publish、PR で can-i-deploy(`--to-environment main`)を実行。 `PACT_BROKER_URL` secret が未設定なら全 step が skip され、本番 Broker hosting が確定するまで dormant な状態で main に置ける。
- **Provider verify の Broker 化は Phase 3.5 として後送り**: 各 Provider test に `@PactBroker` を導入する場合、 base class 抽出 + verify 結果の publish back + consumer version selectors の運用設計が必要で、 Phase 3 のコアバリュー(can-i-deploy で merge 安全性を機械判定)とは独立して進められる。 当面 Provider verify は ci.yml の `mvn verify` 経路(PactFolder)で続ける。

### Phase 3.5(2026-05-07 実施分)— Provider verify の Broker 化

Phase 3 で can-i-deploy のインフラはあったが、 verify 結果が Broker に流れていないと can-i-deploy は常に `unknown` 判定で機能しなかった。 Phase 3.5 でこのループを閉じる。

**実装したもの**:

- **各 Provider test を Folder/Broker の 2 経路に分離**: 4 Provider それぞれで abstract base class(`*ProviderPactBase`)に `@PactVerifyProvider` メソッドを抽出し、 `*ProviderPactTest`(`@PactFolder`、 `pact.providerVerifier.enabled` で gate)と `*ProviderBrokerPactTest`(`@PactBroker`、 `PACT_BROKER_URL` env で gate)が継承する構成。
- **verify 結果の publish back**: `pact.verifier.publishResults=true` + `pact.provider.version` + `pact.provider.tag` を CI から渡す。これで Broker matrix API が「Provider X が Consumer Y バージョン Z に対して verify 緑」と判定できる。
- **CI workflow を 3 段階 job に再構成**: `publish` → `provider-verify`(matrix で 4 Provider 並列)→ `can-i-deploy`(PR のみ)。 main push でも provider-verify が走り、 main の verify 結果が Broker に蓄積されるので、 PR の can-i-deploy が緑になる。
- **`pact-broker.yml` の matrix strategy 採用**: 4 Provider 並列で verify することで wall time 短縮(逐次なら 4x、並列なら 1x)。

**スコープに含まれなかったもの(意図的)**:

- **abstract base 化は不可能**: Pact-JVM 4.6 は `@PactVerifyProvider` メソッドを呼ぶときに **declaring class を no-arg `newInstance()` する** ため、 base class を `abstract` にすると `InstantiationException` を投げる。 base class も concrete に保ち、 surefire の include パターン (`**/*Test.java`) に該当しない `*Base.java` 命名で実行されないようにしている。

**確認(local Broker での round-trip)**:

```
publish: 4 pacts with tag 'local'
provider-verify(全 4 Provider): 5 interaction 全件 OK + verify 結果 publish back
matrix API: deployable=true, success=4, failed=0, unknown=0
```

これで「Provider が Consumer 契約を満たす」事実が Broker に永続化され、 can-i-deploy が機械判定可能な状態になった。

**スコープ外(Phase 3 でも触らなかった)**:

- **REST API の Pact 契約**: BFF ↔ 各サービスや、サービス間 gRPC/REST がもし生まれたとき(現状は Kafka 中心)に追加。 BFF 自体が未実装のため YAGNI。
- **Pact Broker のホスティング先確定**: EKS の admin namespace か Pactflow SaaS か。 Phase 3 完了後に **別 ADR** で運用設計を確定する。 secret は GitHub Actions に流し込む方向だが、 Broker 自体の HA / バックアップ / 認証は別 ADR スコープ。

### ~~Known limitation (Phase 1〜3 共通) — matching rule の strictness~~ → **Phase 4 で解消**

(原文を残してあるのは「過去にここで詰まった」という事実をログとして残すため)

~~`PactBuilder` (V4) + `PactDslJsonBody` (V3 DSL) を組合せて `Map.of("message.contents", payload)` 経由で content を渡すと、 `stringType` / `numberType` / `minArrayLike` 等で宣言した **matching rule が pact JSON に propagate されない**~~

**真の原因(Phase 4 で判明)**: 上記は `PactBuilder.with(Map.of("message.contents", payload))` が **古い経路** で、 V4 native API の `expectsToReceiveMessageInteraction(name, builder→builder)` + `MessageInteractionBuilder.withContents(c→c)` + `MessageContentsBuilder.withContent(DslPart)` が正しい経路だった。 後者を使うと matching rule は完全に propagate される。

修正後パターン:

```java
return builder.expectsToReceiveMessageInteraction(
        "a wholesale order placed event",
        i -> i.withContents(c -> c.withContent(payload)))  // payload = PactDslJsonBody
    .toPact();
```

これで pact JSON の `matchingRules.body` 節に `type` / `integer` / `number` / `regex` / `min` 全 matcher が出力される。

### Phase 4(2026-05-07 実施分)— matching rule の strict 一致を緩和

**実装**:

- 5 経路すべての Consumer Pact test を `expectsToReceiveMessageInteraction(...).withContents(c -> c.withContent(payload))` パターンへ変換(以前の `with(Map.of("message.contents", payload))` から差し替え)。
- 結果として全 pact JSON の `matchingRules.body` が完備に: `numberType / integerType / stringType / minArrayLike / stringMatcher` が宣言したルールを 1:1 で表現。
- `manufacturing.work_order.released.v1` の Provider が **2 components 返却** に戻った(以前は strict 一致のため 1 件固定)。これにより Provider が真に近い回答を返せるようになり、 contract のフィット度が向上。

**Broker round-trip 確認(local)**:

```
publish: 4 pact ファイル全件 OK(matchingRules 節完備)
provider-verify: 全 5 interaction 緑、 verify 結果 publish back 成功
matrix API: deployable=true, success=4, failed=0, unknown=0
```

**残課題**: `LambdaDsl` 完全移行 → **Phase 4.5 で実施**。

### Phase 4.5(2026-05-07 実施分)— `LambdaDsl` 全面移行

`PactDslJsonBody` のメソッドチェーン形式から `LambdaDsl.newJsonBody(o -> { ... })` のネストラムダ形式へ 5 経路すべて書き換えた。

**Before(V3 DSL チェーン形式)**:

```java
PactDslJsonBody itemTemplate = new PactDslJsonBody()
    .integerType("lineNo", 1)
    .stringType("skuCode", "SKU-A");

PactDslJsonBody payload = new PactDslJsonBody()
    .numberType("aggregateId", 5001L)
    .minArrayLike("items", 1, itemTemplate);
```

**After(LambdaDsl ネスト形式)**:

```java
DslPart payload = LambdaDsl.newJsonBody(o -> {
    o.numberType("aggregateId", 5001L);
    o.minArrayLike("items", 1, item -> {
        item.integerType("lineNo", 1);
        item.stringType("skuCode", "SKU-A");
    });
}).build();
```

**意図**:

- ネスト構造(items[].* など)が **インデントで自然に表現** され読みやすい。
- 中間変数(`itemTemplate` 等)が消え、 1 つのリテラルで契約全体が宣言できる。
- Pact-JVM の生成 JSON は不変(機能等価)。 全 5 interaction の `matchingRules.body` パスは Phase 4 と同一。
- Folder / Broker 両経路で Provider verify 緑(再確認済)。

**機能差**: なし。可読性向上のみのリファクタリング。

### スコープ外

- **PactBroker 自体の運用**(管理サービス、認証、SLA 設計)— 必要時に別 ADR で扱う
- **Spring Cloud Contract**(Pact 代替) — 採用しない。理由は Alternative 1 参照
- **Producer-driven contract**(Provider が契約を書く) — Consumer の知識不在で発展段階に合わない

## Consequences

### Positive

- **Schema 進化の影響が機械的に検出できる**。Provider 側で「実害のある変更」と「実害のない変更」を区別する根拠ができる(Consumer が使っていないフィールドの追加・削除は安全と機械判定可能)。
- **Glue Schema Registry と補完**。前者は文法、Pact は使用ベースで二層の防御線。
- **Consumer のドキュメント化**。「inventory-core が `wholesale.order.placed.v1` から何を期待しているか」が Pact ファイルに表明されており、コードを読まなくても把握できる。
- **将来の Pact Broker 導入時、`can-i-deploy` で deploy 安全性が機械判定可能**になる。本 ADR の Phase 1 で Consumer テストの書式を統一しているので、Broker 連携時の追加コストが小さい。
- **既存の KafkaIntegrationE2ETest と棲み分け明瞭**: E2E IT は「正しく動く」、Pact は「契約に合致する」。

### Negative

- **学習コスト**。Pact V4 API は MessagePact 旧 API と書式が異なり、初回はハマる(本 ADR の Phase 1 実装で経験済み、`CHANGELOG.md` の Future Work に記録)。
- **MVP の見返りが小さい**。Consumer 側だけだと Provider 違反は検出できない(自分の期待が文書化されるだけ)。Phase 2 の Provider verify が入って初めて検出機構として完成する。本 ADR は Phase 1 を「将来の足場」と位置付けることで正当化する。
- **Pact ファイルの管理**。Phase 1 では target/ 配下なので毎回再生成される。Phase 2 で Provider verify を導入するときに「どこに置くか」(共有ディレクトリ / Broker)を決める必要がある。

### Neutral

- **Glue Schema Registry の compatibility level の扱い**は本 ADR では変えない。引き続き `BACKWARD_TRANSITIVE` を業態別 topic ごとに設定する(ADR-0016)。
- **Kafka headers**(`tenant_id` / `event_id` / `trace_id` / `schema_version`)は Pact 契約に含めない。これらは payload ではなくメタデータで、 commons-event の OutboxKafkaSender が一律に付ける(ADR-0009)。Pact が headers にも対応していることは認識した上で、MVP では payload 契約のみ。

## Alternatives considered

### Option 1: Spring Cloud Contract を採用する

Pact 代替として Spring 純正の SCC を使う。Groovy DSL での契約記述、Wire Mock ベースの stub 自動生成、 Spring 系プロジェクトとの統合が良い。

**Rejected**:

- Pact-JVM の方が **業界標準**(複数言語クライアント、Pact Broker、`can-i-deploy` 等のエコシステム)
- Kafka メッセージングのサポートが Pact V4 で熟成しており、SCC は REST 寄り(Kafka stub もあるが採用例が薄い)
- Groovy DSL は学習コスト + Java 純正で済むなら Java で書きたい(本プロジェクトは Java)
- SCC の stub 自動生成は WireMock 前提で、既存の Testcontainers + 単一 context IT(ADR-0014)とパターンが噛み合わない

### Option 2: Glue Schema Registry のみで十分とする

スキーマ進化の互換性は Schema Registry で機械チェック済みなので、追加で Pact は不要、と判断する案。

**Rejected**:

- Schema Registry は **構造的互換**(必須フィールド追加 = 互換性違反)はチェックできるが、**「Consumer が使っていない optional フィールドの値域変更」のような実害なし変更が緑、実害あり変更が赤** という判定はできない。
- 業務ルールに依存する制約(例:「`quantity` は 1 以上」「`occurredAt` は ISO-8601 形式」)は schema 表現でも一部は表せるが、Consumer が実際に何を要求しているかは別レイヤ。
- Schema Registry は文法、Pact は使用ベース。Two-layer defense として両方が必要。

### Option 3: Provider-driven contract(Provider が契約を書く)

Wholesale が `SalesOrderPlacedEvent` の出力契約を書き、Consumer が verify する。

**Rejected**:

- 「Consumer が何を必要としているか」は Provider が知らない。Provider が書く契約は実装の写像になり、Consumer 視点での後方互換性が保証されない。
- Consumer-driven は "Consumer が必須要件を表明" → "Provider が満たすか verify" の方向で、組織的にも「使う側が困るかどうか」を判断軸にする慣行に合致する。
- Pact-JVM の API も Consumer-driven が前提で、Provider-driven にすると道具側のサポートが薄い。

### Option 4: 既存の KafkaIntegrationE2ETest だけで十分とする

E2E IT で実際にメッセージを流して動作確認しているので、Pact は不要、とする案。

**Rejected**:

- E2E IT は **現実装の挙動**を検証するので、 「Provider 側で意図せず必須フィールドを削除した」のような変更は、Consumer 側 listener が `@JsonIgnoreProperties(ignoreUnknown = true)` で吸収して動いてしまえば見逃す(silent failure)。
- E2E は時間がかかる(Testcontainers 起動)。Pact は秒オーダで完了。スキーマ違反の検出を高速 feedback ループに乗せる価値がある。
- E2E は「現実の挙動」、Pact は「契約上の期待」。役割が違うので片方では片方を代替できない。

### Option 5: Pact Broker から始める(Phase 2 を Phase 1 にする)

最初から Pact Broker(または Pactflow SaaS)を立て、契約を中央管理する。

**Rejected (now, may revisit)**:

- 現時点で contract が 1 件のみ。Broker のセットアップ + 認証 + バックアップ運用に対するコストが釣り合わない(YAGNI)。
- Provider verify が動いていない段階で Broker を入れても、Broker は contract 公開先として機能しない。
- Phase 2 で Provider verify が入り、契約が 5 件超えた時点で Broker 化を再考する。それまでは reactor 内ローカル参照で十分。

## References

- ADR-0009: Transactional Outbox(Kafka イベント発行の基盤)
- ADR-0014: Cross-service E2E deferred to local-only(本 ADR の Future complement に対応)
- ADR-0015: Saga choreography as default(Kafka 契約が必要な経路の根拠)
- ADR-0016: Per-business-context compensation topics(契約対象トピックの命名規約)
- 実装コミット: [`025803f`](https://github.com/pero3dev/20260504/commit/025803f)(Phase 1 MVP)
- 関連コード:
  - `services/inventory-core/src/test/java/com/example/inventory/core/pact/WholesaleOrderPlacedConsumerPactTest.java`
  - `services/inventory-core/pom.xml`(`au.com.dius.pact.consumer:junit5:4.6.14`)
- 外部参照:
  - [Pact-JVM JUnit 5 Consumer DSL](https://docs.pact.io/implementation_guides/jvm/consumer/junit5)
  - [Pact V4 specification](https://github.com/pact-foundation/pact-specification/tree/version-4)
  - Sam Newman, "Building Microservices" 2nd ed. ch.9 — testing strategies including CDCT

## Follow-up tasks

- ✅ Phase 2.1: `wholesale-service` 側で `inventory-core-wholesale.json` を verify する JUnit テスト(完了)
- ✅ Phase 2.2: Consumer 契約の追加(retail / manufacturing / shipped / 3PL movement)(完了、 5 経路 / 4 業態)
- ✅ Phase 2.3 = Phase 3: Pact Broker 導入(完了)
- ✅ Phase 3.5: Provider verify の Broker 化 + verify 結果 publish back(完了)
- ✅ Phase 4: matching rule strict 一致を緩和(V4 native API へ移行で完了)
- ✅ Phase 4.5: `LambdaDsl` 全面移行(完了、可読性向上のみ)
- Phase 5 候補: consumer version selectors の本格運用(現状は branch=main / pr-N の単純取得)
- 別 ADR: Pact Broker のホスティング先確定(EKS namespace / Pactflow SaaS、 HA / 認証 / バックアップ)
