# Changelog

このリポジトリの主要な変更履歴。書式は [Keep a Changelog](https://keepachangelog.com/ja/1.1.0/) に準拠し、本プロジェクトは [Semantic Versioning](https://semver.org/lang/ja/) を採用予定(現在は `0.0.1-SNAPSHOT` 単一バージョン)。

詳細な設計判断は `docs/adr/` の各 ADR を、各機能の意図はコミットメッセージを参照。

---

## [Unreleased]

### Highlights

13 サービス(共通基盤 9 + 業態 4)のスキャフォールディング、業態 → Inventory Core の Saga 連結 4 経路 + Manufacturing 完成品 INBOUND 失敗時補償、業態 OUTBOUND/Cancel フローの完成、Workflow SLA 中央タイマ、J-SOX 監査(WORM + ハッシュチェーン + Merkle anchor)実装、Pact 契約テスト Phase 5 + ADR-0021 で本番ホスティング決定までを完了。13/13 サービス + 21 ADR + 8 E2E テストケース。

### Added

#### 共通基盤サービス(9/9)

- **identity-broker**: JWT 発行 + テナント解決(ADR-0007)。Cognito フェデレーションは将来。
- **master-data**: SKU / Location / Partner マスタ。Bridge 方式 + Outbox。コミット [`bcd58c2`](https://github.com/pero3dev/20260504/commit/bcd58c2)
- **inventory-core**: 在庫状態の唯一の書込権威(ADR-0002, ADR-0004)。Reserve / Ship / Receive / Release を備える。
- **inventory-read-model**: Kafka 購読 → Redis 投影、CQRS Read 側(ADR-0004)。
- **audit-service**: AOP 取込 + SHA-256 ハッシュチェーン + 日次 Merkle anchor + DB WORM(ADR-0008、commit [`280e174`](https://github.com/pero3dev/20260504/commit/280e174))。
- **notification**: Kafka 駆動の通知配信(送信器 port 抽象)。コミット [`59876ab`](https://github.com/pero3dev/20260504/commit/59876ab)
- **analytics**: 業態系イベント集計 → daily_order_summary(Pool 方式)。コミット [`4095175`](https://github.com/pero3dev/20260504/commit/4095175)
- **workflow**: Saga オーケストレータ MVP(ADR-0015 の判定基準で導入条件付き)。コミット [`4e26a5d`](https://github.com/pero3dev/20260504/commit/4e26a5d)
- **integration-hub**: 外部連携(EDI / S3 / SFTP / EC)の足場。MVP は CSV 1 アダプタ。コミット [`3e46a64`](https://github.com/pero3dev/20260504/commit/3e46a64)

#### 業態サービス(4/4)

- **retail-ec**: B2C 注文受付 + `retail.order.placed.v1` 発行(コミット [`5d2668d`](https://github.com/pero3dev/20260504/commit/5d2668d))。L2 で出荷確定フロー追加(コミット [`70236a6`](https://github.com/pero3dev/20260504/commit/70236a6))。
- **tpl**: 3PL 入出庫管理 + `tpl.stock.movement.v1`。コミット [`12942f3`](https://github.com/pero3dev/20260504/commit/12942f3)
- **wholesale**: B2B 取引先別契約価格 + 受注 + 出荷フロー。コミット [`52d0551`](https://github.com/pero3dev/20260504/commit/52d0551) / [`853a168`](https://github.com/pero3dev/20260504/commit/853a168)
- **manufacturing**: BOM + WorkOrder。L3 で完成品 INBOUND まで閉じる。コミット [`8fe1f9d`](https://github.com/pero3dev/20260504/commit/8fe1f9d) / [`d226973`](https://github.com/pero3dev/20260504/commit/d226973)

#### 業態 → Inventory Core の Saga 配線(全 4 経路)

| 業態 | 経路 | コミット |
|---|---|---|
| Retail/EC | Phase 1+2(reserve + 補償) | [`65e9c60`](https://github.com/pero3dev/20260504/commit/65e9c60) [`41c5467`](https://github.com/pero3dev/20260504/commit/41c5467) |
| 3PL | D6(stock movement → reserve+ship/receive) | [`ea282e2`](https://github.com/pero3dev/20260504/commit/ea282e2) |
| Wholesale | D9(reserve + 補償) | [`3d243d3`](https://github.com/pero3dev/20260504/commit/3d243d3) |
| Manufacturing | D10(部品 reserve+ship + 補償) | [`a0c93ca`](https://github.com/pero3dev/20260504/commit/a0c93ca) |

#### 業態 OUTBOUND/Cancel フロー(L1/L2/L3 + 取消強化)

- **L1 Wholesale**: 出荷確定 → `wholesale.order.shipped.v1` → ship。コミット [`853a168`](https://github.com/pero3dev/20260504/commit/853a168)
- **L2 Retail/EC**: 出荷確定 → `retail.order.shipped.v1` → ship。コミット [`70236a6`](https://github.com/pero3dev/20260504/commit/70236a6)
- **L3 Manufacturing**: WorkOrder.complete → `manufacturing.work_order.completed.v1` → 完成品 receive。コミット [`d226973`](https://github.com/pero3dev/20260504/commit/d226973)
- **業務取消 → reserved 解放**: `<業態>.order.cancelled.v1` → `Inventory.release(qty)`。`Order.cancel()` と `Order.cancelAfterReservationFailure()` を分離(ADR-0018)。コミット [`e1b2ca2`](https://github.com/pero3dev/20260504/commit/e1b2ca2)

#### 監査ハードニング(D3、ADR-0008)

- **AOP @Auditable** で全状態変化をキャプチャ(commons-audit)
- **SHA-256 ハッシュチェーン** + チェーン整合性検証 REST(`AuditChainVerifier`)
- **日次 Merkle anchor** + 検証 REST(`Sha256MerkleTreeCalculator` + `ComputeDailyMerkleAnchorService`)
- **DB レベル WORM トリガ** で `audit_record` / `audit_merkle_anchor` の UPDATE/DELETE 拒否

#### ADR(18 本)

| # | タイトル | 状態 |
|---|---|---|
| 0001-0013 | プロジェクト基本方針 | Accepted(本セッション以前) |
| [0014](docs/adr/0014-cross-service-e2e-deferred-to-local-only.md) | Cross-service E2E deferred to local-only | Accepted |
| [0015](docs/adr/0015-saga-choreography-as-default-orchestration-on-demand.md) | Saga choreography as default | Accepted |
| [0016](docs/adr/0016-per-business-context-compensation-topics.md) | Per-business-context compensation topics | Accepted |
| [0017](docs/adr/0017-reserve-vs-reserve-ship-selection.md) | Reserve vs Reserve+Ship 使い分け | Accepted |
| [0018](docs/adr/0018-cancel-vs-cancel-after-reservation-failure.md) | cancel メソッド使い分け | Accepted |

#### CI / Test 強化

- **KafkaIntegrationE2ETest** 8 ケース — 業態 4/4 全成功パス + Master 投影 + 認証 + 直接 reserve(コミット [`953cd98`](https://github.com/pero3dev/20260504/commit/953cd98) / [`c6dd5c0`](https://github.com/pero3dev/20260504/commit/c6dd5c0) / [`524a28b`](https://github.com/pero3dev/20260504/commit/524a28b) / [`a497f62`](https://github.com/pero3dev/20260504/commit/a497f62))
- **mvn -T 1C 並列化** — verify 時間 ~50% 短縮(2:16 → 1:10)。コミット [`56341c1`](https://github.com/pero3dev/20260504/commit/56341c1)
- **Pact consumer-driven 契約テスト MVP** — `wholesale.order.placed.v1` の Consumer 期待形式を Pact ファイル出力。コミット [`025803f`](https://github.com/pero3dev/20260504/commit/025803f)
- **Pact Phase 2.1 — Provider verify** — Wholesale 側の `SalesOrderPlacedEvent` が Consumer 契約を満たすか verify(コミット [`79c794b`](https://github.com/pero3dev/20260504/commit/79c794b))。
- **Pact Phase 2.2 — 4 経路の契約追加** — `retail.order.placed.v1` / `wholesale.order.shipped.v1` / `manufacturing.work_order.released.v1` / `tpl.stock.movement.v1` の Consumer/Provider 契約(コミット [`ed01832`](https://github.com/pero3dev/20260504/commit/ed01832))。
- **Pact Phase 3 — Broker + can-i-deploy** — 1) `infra/pact-broker/` に local Broker docker-compose + README、2) `inventory-core` に `pact:publish` Maven plugin、3) `.github/workflows/pact-broker.yml` で main push 時 publish + PR 時 can-i-deploy(secret 未設定時は skip)。 ADR-0019 Phase 3 セクション追加。
- **Pact Phase 3.5 — Provider verify の Broker 化** — 各 Provider test を `*ProviderPactBase`(共通) / `*ProviderPactTest`(Folder source) / `*ProviderBrokerPactTest`(Broker source、 `PACT_BROKER_URL` env でゲート)の 3 ファイル構成に分離。 `pact.verifier.publishResults=true` で verify 結果を Broker に publish back。 `pact-broker.yml` を `publish → provider-verify(4 並列 matrix) → can-i-deploy` の 3 段 job に再構成。 local Broker で round-trip 確認済(matrix API: `deployable=true, success=4, failed=0`)。
- **Pact Phase 4 — matching rule strict 一致を緩和** — Consumer Pact test を `expectsToReceiveMessageInteraction(name, i -> i.withContents(c -> c.withContent(payload)))` の V4 native API へ全 5 経路で書き換え。 これで `PactDslJsonBody` 上の `numberType / stringType / integerType / minArrayLike / stringMatcher` がすべて pact JSON の `matchingRules.body` に propagate される。 旧 `with(Map.of("message.contents", payload))` 経路は matching rule を **黙って捨てる** 落とし穴で、 ADR-0019 の "Known Limitation" として記録していたが Phase 4 で根治。 manufacturing Provider を 2 components 返却に戻す副作用付き。
- **Pact Phase 4.5 — `LambdaDsl` 全面移行** — Consumer Pact test を `PactDslJsonBody` チェーン形式から `LambdaDsl.newJsonBody(o -> {...})` ネストラムダ形式へ 5 経路すべて書き換え。 ネスト構造(`items[].*` など)がインデントで自然に表現され、中間変数の `itemTemplate` 等が消えた。 機能等価(matchingRules パス完全一致、 Folder/Broker 両経路で Provider verify 緑)、可読性向上のみ。
- **Pact Phase 5 — Consumer version selectors の本格運用** — 各 ProviderPactBase に `@PactBrokerConsumerVersionSelectors` を追加。 `mainBranch()`(プロダクション safety net)+ `deployedOrReleased()`(後方互換 safety)+ `branch(<provider branch>)`(PR 連動)の 3 selector で Broker から Consumer pact を取得。 `pact.consumer.branch` で publish に branch metadata を付与、 `pact-broker.yml` を branch-aware に。 Pact-JVM 4.6 の `matchingBranch()` バグ(`providerVersionBranch` 未付与で 400)を `branch(System.getProperty(...))` で回避。 main / pr-N 両ケースで Broker round-trip 確認済。
- **ADR-0021 Phase 1 — Pact Broker 本番デプロイ用 manifests** — `infra/pact-broker/k8s/`(Namespace / SA(IRSA)/ ConfigMap / Secret(External Secrets template)/ Deployment(2 replicas, RuntimeDefault seccomp, topology spread)/ Service(ClusterIP)/ Ingress(ALB internal, ACM)/ HPA(2-3, CPU 60%)/ NetworkPolicy(ALB → broker / broker → Aurora 5432 + DNS)) + `argocd/application.yaml`(GitOps)+ `db/001-create-pact-broker-db.sql`(Aurora-C 切り出し)+ README に Step 1〜8 ランブック。 ACM ARN / IRSA ARN / VPC CIDR / DB host は placeholder で、本番 PR 時に環境別値で patch する想定。 全 11 manifests が YAML parse 緑。
- **ADR-0021 Phase 2 — Cognito SSO 連携(人間 UI 経路)** — `ingress.yaml` を `ingress-ui.yaml`(`pact-broker.internal.example.com`、 ALB Cognito auth、 8h session)+ `ingress-api.yaml`(`pact-broker-api.internal.example.com`、 Basic Auth 直結)に分割。 同一 ALB を `IngressGroup` で共有。 Pact Broker 自体の Basic Auth は据え置き(二段認証 UX を許容)。 CI は API hostname に切替えるだけで Basic Auth credential 不変。 README に Phase 2 ランブック(Cognito User Pool App Client 作成 → ingress patch → DNS 追加 → GitHub secret 更新)。 シームレス SSO(JWT → Basic Auth 注入)は Phase 2.5 候補で後送り。
- **ADR-0021 Phase 2.5 — nginx sidecar による完全シームレス SSO** — Pact Broker pod に `nginx:1.27-alpine` sidecar を同居させ、 ALB Cognito auth 通過後の UI 経路で `Authorization: Basic <read-only-cred>` を自動注入。 Phase 2 の二段認証 UX(Cognito + Basic Auth ダイアログ)を解消し、 ユーザは Cognito 1 回サインインだけで Pact Broker UI が即時表示される。 CI 経路は sidecar バイパスで Pact Broker へ直接到達(別 Service named port)。 注入する credential は read-only 固定で Phase 3(社内全員 read-only 公開)と整合。 oauth2-proxy ではなく nginx を選んだのは、 ALB が既に Cognito auth を完了させており OIDC handshake 重複が不要なため(image 5MB / memory 32MB の軽量)。
- **ADR-0021 Phase 3 — UI を engineering org 全員に read-only 公開** — `pdb.yaml`(PodDisruptionBudget、 `minAvailable: 1`)を追加し voluntary disruption(node drain / cluster autoscaler / rolling update)時に常時 1 replica を保持。 NetworkPolicy に sidecar 9293 ingress を補完(Phase 2.5 の追加漏れを塞ぐ)。 README に Phase 3 デプロイ手順 + Cognito access policy(全 eng org or engineering group)+ 社内告知テンプレ + 運用ノート(load 想定 / Aurora-C 負荷 / 退職者アクセス取り消し)を追加。 注入 credential が read-only 固定なので audience 拡大しても write 操作は CI 経由のみ、 安全に開放可能。

#### ビジネスフロー残ロジック(B1 / B2)

- **B1 Manufacturing 完成品 INBOUND 失敗時の補償フロー** — `WorkOrder.complete` 後の `inventory.work_order.completed.v1` Saga で完成品 INBOUND が失敗するケースを補償。 `WorkOrderCompletionFailedEvent` を `manufacturing.completion.failed.v1` トピックへ Outbox 経由で発行する `EmitWorkOrderCompletionFailedService`(`REQUIRES_NEW`)を `inventory-core` に追加し、 `WorkOrderCompletedListener` が補償経路を起動。 manufacturing 側 `CompletionFailedListener` + `HandleCompletionFailureService` は WorkOrder 状態を巻き戻さず audit + warn のみ(完成品の物理的不可逆性)。 部品消費失敗時のキャンセルとは非対称(ADR-0017 follow-up)。 コミット [`963a289`](https://github.com/pero3dev/20260504/commit/963a289)
- **B2 Workflow SLA timeout(中央タイマ)** — ADR-0015 B2 の中央タイマ実体。 各 `WorkflowDefinition.instanceSla()` を見て `now - startedAt > sla` の STARTED インスタンスを 30 秒間隔で FAILED に強制遷移させ `workflow.instance.completed.v1` を発行する。 `WorkflowInstance.expireIfOverdue` で集約内遷移 + イベント発行、 `ExpireOverdueWorkflowsService` は per-instance 独立 TX(`TransactionTemplate`、 self-invocation の `@Transactional` 不発を回避)で 1 件失敗が他に波及しない。 `WorkflowSlaScheduler` は `@ConditionalOnProperty(platform.workflow.sla.enabled)` で test/loadtest profile では無効化可能。 `ApprovalFlow` は 24h SLA。 イベント駆動の Workflow 自動 step handler は別タスク(per-event-source カスタムロジック規模)として保留。 コミット [`43fc896`](https://github.com/pero3dev/20260504/commit/43fc896)

### Changed

- **`inventory.reservation.failed.v1` → `retail.reservation.failed.v1` 改名**(L4、ADR-0016 follow-up)。Phase 2 で命名規則が固まる前に作った共通名前空間を、業態別命名に揃える。コミット [`3cc68f3`](https://github.com/pero3dev/20260504/commit/3cc68f3)
- **`HandleReservationFailureService` を `cancelAfterReservationFailure()` 呼出に切替**(ADR-0018)。Reserve 失敗の補償経由で release イベントが発行されると Inventory Core が `InsufficientReserved` で失敗する事故を回避。
- **CI workflow `mvn verify` を `mvn -T 1C verify` に変更**(コミット [`56341c1`](https://github.com/pero3dev/20260504/commit/56341c1))。

### Fixed

- **OutboxPublisher の self-invocation で `@Transactional` が効かなかった問題**を修正。`search_path` が tenant スキーマに切り替わらず、Bridge 方式マルチテナンシーが破綻していた。コミット [`5089e58`](https://github.com/pero3dev/20260504/commit/5089e58)
- **e2e-tests の HikariPool 競合**(多重 Spring Context 同居)を解消(コミット [`7a3b39e`](https://github.com/pero3dev/20260504/commit/7a3b39e))。 後に ADR-0014 で「e2e-tests は CI 外しに、KafkaIntegrationE2ETest を canonical に」と方針確定(コミット [`cc73e82`](https://github.com/pero3dev/20260504/commit/cc73e82))。
- **MyBatis `<constructor>` の javaType エイリアスを primitive に修正**(コミット [`696d225`](https://github.com/pero3dev/20260504/commit/696d225))。
- **Maven core プラグインを明示ピン**(コミット [`acea224`](https://github.com/pero3dev/20260504/commit/acea224))。Maven バージョン差異で未リリース版を参照する事故を回避。
- **`@SpringBootTest(classes=...)` 明示時にネスト `@TestConfiguration` が自動検出されない問題**を修正(コミット [`baaad32`](https://github.com/pero3dev/20260504/commit/baaad32))。
- **CI `ci.yml` の YAML parse 失敗**を修正(コミット [`00c50c5`](https://github.com/pero3dev/20260504/commit/00c50c5))。

### Repository Statistics

- **Modules**: 26(commons 10 + services 13 + 親 + e2e + commons-bom)
- **業態 / 共通基盤**: 4/4 + 9/9 = 13/13 services
- **Saga 配線**: 4/4 業態(Manufacturing 完成品 INBOUND 失敗補償含む)
- **Workflow SLA**: 中央タイマで STARTED インスタンスを 30 秒間隔 scan、 ApprovalFlow 24h SLA を strict 適用
- **ADR**: 21 本(ADR-0021 で Pact Broker 本番ホスティングを EKS self-host on Aurora-C に確定)
- **E2E IT**: 8 ケース(`KafkaIntegrationE2ETest`)
- **Contract Test**: 5 経路 / 4 業態(Pact、Consumer + Provider verify Folder + Provider verify Broker) + Broker publish + can-i-deploy + verify result publish back + matchingRules 完備 + LambdaDsl + branch-aware selectors(ADR-0019 Phase 3 / 3.5 / 4 / 4.5 / 5 完了)

### Future Work

#### A. インフラ実展開 — Pact Broker(ADR-0021 Phase 1〜3 manifests 適用)

- Aurora-C 内 `pact_broker` DB の切り出し(`infra/pact-broker/db/001-...sql`)
- IRSA Role + ExternalSecretsOperator マニフェスト(`prod/pact-broker/*` secret 同期)
- ALB Cognito 用 User Pool App Client 作成(identity-broker と連携)
- ArgoCD Application apply + Route53 internal alias 登録
- GitHub Actions secret 投入(`PACT_BROKER_URL` / `_USERNAME` / `_PASSWORD`)→ `pact-broker.yml` workflow が dormant 解除
- Phase 3 社内告知(README の告知テンプレを流用)

#### B. ビジネスフロー(残ロジック)

- Workflow 自動 step handler — orchestration が必要な candidate(ADR-0015):業態横断承認、 EDI ACK 待ち、 WorkOrder ライフサイクル全体。 per-event-source カスタムロジックが必要(SLA timeout = 中央タイマは B2 で実装済、 step 推進ロジックが未実装)

#### C. 監査・コンプライアンス(ADR-0008 未実装)

- audit-service S3 Object Lock(Compliance mode)への Parquet 投入(現状は DB 内 anchor のみ)
- Merkle root の S3 二重保管(現状は `audit_merkle_anchor` テーブルのみ)
- Athena 経由のクエリ
- 1 年保持期限による自動失効

#### D. 外部連携(Integration Hub)

- 実 adapter 拡充: S3 / SFTP / AS2-EDI / 外部 EC(現状は CSV 1 アダプタの足場のみ)

#### E. テスト基盤

- 多重 Spring Context IT(`e2e-tests/`)の CI 復帰(技術スタック進化次第、 ADR-0014)
- Nightly E2E — 単一 context IT + Pact + 結合動作の三層 testing 構想の最終層(ADR-0014 Future complement)
